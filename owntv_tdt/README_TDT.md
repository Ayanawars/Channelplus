# OwnTV TDT 2 — canales y selección de grupos

Compilación experimental personal basada en OwnTV v5.2.1 y OwnTV Core 1.0.69, con el sintonizador interno de TCL y listas IPTV. No es una publicación oficial de OwnTV.

## Novedades

- Acceso **Modificar canales y grupos** desde cada fuente en Ajustes → Listas de reproducción.
- En la fuente Antena / TDT, ese acceso abre directamente sus canales. Se pueden ocultar, mostrar, renombrar y ordenar con las herramientas de OwnTV.
- En las fuentes IPTV: botones **Seleccionar todos**, **Deseleccionar todos** y **Elegir grupos**. Al deseleccionar todos se muestran todos los grupos en el editor con el selector desactivado. OK marca o desmarca cada grupo; **Terminar selección** vuelve a la edición habitual.
- Los cambios se guardan automáticamente por perfil y sección. La acción masiva de una fuente solo cambia sus grupos. No borra canales, favoritos, nombres personalizados ni el orden de otras listas. Los cambios afectan a OwnTV, sin modificar la lista de TCL.
- Continúan las limitaciones de la primera prueba: TDT en directo, sin pausa, timeshift, grabación ni selección de pistas propias del sintonizador. La reproducción de TDT de la primera versión fue confirmada por el usuario; esta interfaz nueva necesita validación con el mando en su TV.

## Pasar desde la primera versión

La clave de firma del primer APK no se conservó. Esta versión usa otro paquete, `tv.own.owntv.tdt2`, y aparece como **OwnTV TDT 2**. Se instala aparte, por lo que no elimina los datos de OwnTV TDT.

1. En la primera app, abre **Ajustes → Copia de seguridad y restauración → Exportar una copia de seguridad**. Incluye las listas, perfiles y personalizaciones que quieras trasladar. Usa una contraseña de copia para conservar también las credenciales guardadas de IPTV.
2. Instala `OwnTV-TDT-5.2.1-test2.apk` en la televisión mediante Bugjaeger.
3. En la consola de Bugjaeger, conectada a la TV, ejecuta:

   ```sh
   pm grant --user 0 tv.own.owntv.tdt2 android.permission.READ_TV_LISTINGS
   ```

4. Abre **OwnTV TDT 2** y restaura la copia de OwnTV. También puedes configurar las fuentes de nuevo si prefieres.
5. Entra en **Ajustes → Listas de reproducción → tu fuente → Modificar canales y grupos**.

La firma de esta versión se conserva junto al proyecto para poder actualizarla en siguientes compilaciones.

## Código y compilación

Bases originales:

- https://github.com/ahXN00/OwnTV, etiqueta `v5.2.1`, commit `8f47da68bf246b4ccbf9e53a53a81b4bf0620d2e`.
- https://github.com/ahXN00/OwnTV_Core, etiqueta `core-1.0.69`, commit `1de78b1e2690c22b50c060e986bce221454bf880`.

Se conservan las licencias y los avisos de los autores originales. Los cambios se proporcionan bajo GPLv3, con las fuentes completas de ambos proyectos. Las dependencias mantienen sus licencias; las fuentes de la biblioteca mpv utilizada están en https://github.com/ahXN00/OwnTV_libmpv.

Requisitos: JDK 21, SDK Android Platform 37.0 y Build Tools 37.0.0. Configura `ANDROID_HOME` o el `sdk.dir` local. Desde `owntv_tdt`:

```sh
./gradlew --no-daemon -Powntv.corePath=../owntv_core_tdt -Pksp.incremental=false -Pkotlin.incremental=false :app:assembleStandardDebug
```

La copia de fuentes personal de la versión 2 (fuera de este repositorio) incluye `owntv_tdt/test-signing.jks`, la firma de pruebas usada para esta versión (contraseña `android`, alias `tunerprobe`). Consérvalo si vas a compilar una actualización compatible con este APK. No hace falta abrirlo ni instalarlo para usar la aplicación.

Comprobaciones seleccionadas: inventario de textos traducibles, compilación ARM, firma APK y pruebas JVM de direcciones de canales, restricciones de grabación y conservación del orden entre listas. No se dispone de la TV física en este entorno para probar la navegación nueva.
