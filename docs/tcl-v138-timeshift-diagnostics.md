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

**Pendiente:** validar en la TCL V138. Compilar, instalar y ejecutar sobre la TV; estos registros no prueban por sí mismos que el Timeshift funcione en apps de terceros.
