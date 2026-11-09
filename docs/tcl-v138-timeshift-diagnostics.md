# TCL V138: diagnóstico TDT Timeshift (STAYON+)

El Timeshift funciona en la aplicación de TV original de TCL con el almacenamiento USB ya configurado. Eso no confirma que TCL lo exponga mediante Android TV Input Framework (TIF) a STAYON+.

Esta rama registra la sesión TIF y añade una **prueba manual y reversible de pausa/reanudación con la API pública de Android**. No instala aplicaciones de TCL, no activa modos ocultos, no modifica PVR, no cambia permisos ni firmware y no fuerza `TIME_SHIFT_STATUS_AVAILABLE`.

## Prueba controlada de pausa y reanudación (solo APK de diagnóstico)

El registro de la TCL V138 mostró `TIME_SHIFT_INVALID_TIME` y `UNKNOWN`, sin búfer TIF válido. Una prueba de `TvView.timeShiftPause()` es la siguiente verificación, **no una solución confirmada**.

1. Instalar la **nueva** APK de diagnóstico como aplicación independiente `tv.own.owntv.tdt2.preview`.
2. Abrir un canal TDT de TCL en STAYON+ Diagnóstico y mostrar el HUD con OK.
3. En el pie de la pantalla, pulsar **Pausar TV (prueba)**. Comprobar con los ojos si imagen y audio quedan pausados.
4. Pulsar **Reanudar TV (prueba)** y comprobar si vuelve la emisión. Incluso si Android acepta el comando, TCL puede ignorarlo.
5. En Bugjaeger, consultar `logcat -d -t 5000 -v time | grep StayonTimeshift` y buscar `tcl_probe`, `status`, `start_position` y `current_position`.
6. Si la pausa no funciona, informar del resultado y **no** intentar rebobinar: el código no permitirá búsquedas sin límites válidos del búfer.

Los botones se muestran solo en el APK `.preview` sobre la entrada `com.tcl.tvinput/`; en la aplicación oficial no aparece esta opción experimental. Solo la persona que pulsa envía los comandos. `tcl_probe dispatched` significa que se envió la orden sin excepción, **no** que la TV la ejecutó.

Las posiciones iniciales inválidas repetidas se registran una sola vez por valor, evitando saturar Logcat.

## Hallazgo confirmado: análisis de los APK TCL originales (9 noviembre 2026)

Recuperados de la biblioteca de archivos existente; **no es necesario volver a extraerlos de la TV**:

- `TIF_LiveTV.apk`, SHA-256 `d84372dcd33f56177beb43ff58aeee1121bf930100a7d82f25e38019b3dc83b5`.
- `TIF_TvInput.apk`, SHA-256 `d530fd42884355dfb36d5be314b8a8a3d12225478397af1dd53bd609a6183b24`.
- También existe el análisis anterior `TCL-LOVEStv-analisis-V138.zip`, que describe comprobaciones de autorización TVSDK en el firmware.

Inspección estática de manifiestos y tablas de métodos de `classes.dex`:

1. `com.tcl.tvinput.TvInputSession` hereda de `android.media.tv.TvInputService.Session` y **no declara** `onTimeShiftPause`, `onTimeShiftResume`, ni `onTimeShiftSeekTo`. Su APK no tiene métodos nombrados `onTimeShift*` en la tabla DEX. Es evidencia sólida de que esa implementación del TV Input Framework no publica las operaciones de Timeshift a STAYON+.
2. `TIF_LiveTV.apk` sí implementa `com.tcl.tv.timeshift.TimeShiftMainActivity`, `com.tcl.tv.google.timeshift.TimeShiftManager` y referencias a una API propietaria externa `com.tcl.tvmanager.TDtvPvrManager`. Esta última ofrece `startTimeshiftRecord`, `timeShiftPlayback`, `returnToLive`, `getTShiftPlaybackPosition` y `stopThimeshiftRecord`.
3. Los dos APK tienen `android:sharedUserId="android.uid.system"` en el manifiesto y solicitan permisos de sistema. Las operaciones propietarias no deben asumirse disponibles para una aplicación de terceros; la presencia de un método referenciado no demuestra que sea una API pública.
4. La prueba física del usuario confirmó que `TimeShiftMainActivity` pausa/reanuda en la app TCL, mientras el TIF externo informa estado `UNKNOWN` y tiempo inválido `Long.MIN_VALUE`. Al pulsar el botón experimental, el log muestra `tcl_probe request=pause` seguido de `tcl_probe dispatched ... confirmed=false`, pero la imagen continúa en directo.

**Conclusión:** no es un error del control de pausa de STAYON+. El `TvInputService.Session` de esta TCL no implementa los callbacks Timeshift que nuestra app necesita. No hay una solución comprobada basada solo en `TvView.timeShiftPause()`. Integrar la ruta propietaria requeriría conocer una interfaz oficialmente accesible a terceros y verificar sus autorizaciones; no basta copiar o invocar clases internas.

**Recomendación técnica:** mantener la funcionalidad experimental fuera de `main`. Investigar únicamente interfaces disponibles para una app sin privilegios de sistema, o acordar acceso soportado con TCL. No modificar firmware, valores de fábrica, permisos de sistema ni el USB; no anunciar un Timeshift funcional que no esté confirmado por pruebas.

## Nueva prueba: abrir el controlador TCL desde STAYON+

Inspección del **manifiesto real** de `TIF_LiveTV.apk`: la actividad `com.tcl.tv.timeshift.TimeShiftMainActivity` declara el filtro de intención `com.tcl.tv.action.TIMESHIFT` y **no declara `android:exported` explícitamente**. En las versiones de Android anteriores a la obligatoriedad de ese atributo, un filtro de intención puede implicar que el componente sea exportado. Esto **no garantiza** que el fabricante permita el lanzamiento desde otra aplicación, ni que Timeshift disponga de un canal activo.

Solo en la APK `.preview`, cuando reproduce el sintonizador `com.tcl.tvinput/`, se muestra el botón **Timeshift TCL** junto al de pausa de diagnóstico. Al pulsarlo:

1. Se construye una intención **explícita** con la acción documentada en el manifiesto y el componente `com.tcl.tv/com.tcl.tv.timeshift.TimeShiftMainActivity`. No se requiere root.
2. Se llama `startActivity`; si Android rechaza la llamada, la aplicación registra `tcl_bridge exception` y mantiene la sesión TDT original de STAYON+.
3. Si Android acepta el lanzamiento, se libera la sesión TIF de STAYON+ para que TCL pueda adquirir el sintonizador, y se registra `tcl_bridge dispatched tuner_released=true`.
4. Se abre **la interfaz original de TCL**, no una interfaz interna incrustada en STAYON+. Al salir con Atrás, se regresa a nuestra aplicación; puede ser necesario volver a sintonizar el canal.
5. Comprobar visualmente si la pantalla TCL permite pausar/reanudar y recuperar `logcat -d -v time -s StayonTimeshift:V '*:S' | tail -n 80`.

No asumir que `dispatched` significa que la TV realmente haya activado Timeshift. Esta prueba identifica si el componente exportado acepta arrancar y si el traspaso del sintonizador funciona; **no resuelve todavía una integración de la barra de pausa de TCL dentro del reproductor STAYON+**.

## Captura con Bugjaeger

1. Instalar una compilación de STAYON+ que incorpore este cambio.
2. Abrir STAYON+, sintonizar un canal TDT y esperar al menos 20–30 segundos.
3. Probar pausa, retroceso y volver al directo **solo si la interfaz ofrece esos controles**. Si están deshabilitados, basta con sintonizar el canal.
4. En Bugjaeger, consola Shell, ejecutar:

```sh
logcat -d -t 5000 -v time | grep StayonTimeshift
```

Si el registro está vacío, comprobar que se instaló la compilación correcta y que se ha sintonizado TDT desde STAYON+. No hace falta usar `logcat -c` ni root.

## Cómo interpretar los eventos

- `tune ...` / `video_available ...`: la sesión TIF ha intentado sintonizar y ha recibido vídeo.
- `status ... AVAILABLE`: el proveedor TIF anuncia Timeshift a STAYON+.
- `status ... UNAVAILABLE/UNSUPPORTED/UNKNOWN`: la sesión no lo anuncia como disponible.
- Ausencia de `status` tras `video_available`: no se recibió la notificación durante la prueba. Por sí sola no permite saber si TCL oculta o no implementa el callback.
- `start_position`, `current_position`: datos enviados por el proveedor TIF. La posición actual se registra al primer evento y después como máximo cada 15 segundos.
- `pause blocked`, `seek blocked`, `go_live blocked`: STAYON+ impidió la orden por ausencia de vista o disponibilidad confirmada.
- `command ... dispatched`: STAYON+ llamó a la API de Android **sin excepción**; no confirma que TCL ejecutara la orden.
- `command ... exception`: Android devolvió una excepción y el estado se reinició manteniendo la TDT.
- `mode_auto not_requested api_below_34`: comportamiento esperado en Android 11; esa API se incorporó después y no puede invocarse allí.

No compartir más datos de Logcat que los filtrados si contienen información privada de otras aplicaciones.

**Estado:** prueba física realizada en TCL V138. La sesión TIF estándar no proporciona Timeshift, y la pausa experimental se envía pero no funciona. La vía propietaria de TCL sigue sin integración compatible con apps de terceros.
