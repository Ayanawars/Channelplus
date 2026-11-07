# STAYON+ — Configuración integral (especificación acordada)

> Documento de implementación; no confundir las funciones **acordadas** con las que ya están desarrolladas. Estado de referencia: versión publicada **5.4.3**. No publicar una APK hasta pasar las pruebas y firmarla con la misma clave de las versiones instaladas.

## Sistema visual y navegación

- Android TV / mando TCL, **no interfaz de móvil**. Fondo negro puro `#000000` y estética plana, sobria y premium.
- Acento **amarillo limón pálido** (referencia de diseño `#F4F29A`), texto cálido (blanco roto), grises neutros; selección de fondo limón con texto negro.
- Interruptores activos: pista amarillo pálido, **círculo negro**, sin blanco sobre amarillo; respetar estados inactivos.
- **Sin glassmorphism, transparencias decorativas, glow, degradados, brillos, neones, 3D ni sombras llamativas.**
- Navegación tres zonas: categorías → subapartados → valores/detalles; foco claramente visible y estable con el mando; restaurar foco al volver.
- Barra de búsqueda global y Ajustes rápidos (pines/reordenar). No duplicar preferencias funcionales.
- **Cero perfiles** en STAYON+: sin selector inicial, perfiles infantiles, perfil en menú, datos de perfil en backups, ni opciones fantasma en búsqueda. Migración de datos existentes sin perder favoritos, historial ni configuración.
- Antes de General: **Ajustes del televisor** abre Ajustes nativos de Android TV/TCL; no sustituirlos con copia.
- Estructura de nueve categorías (en este orden): General; Fuentes; Home; Reproducción; Apariencia e interfaz; Mando y navegación; Música y servicios; Aplicaciones; Sistema y mantenimiento.
- Aplicar escala individual por sección y tamaño de UI Home, sin afectar vídeo ni el resto de la app. Logos bien centrados y con transparencia; no fondos grises.

## 1. General

1. Inicio de aplicación: Home / TV / último canal / Radio / sección específica.
2. Vídeo de introducción STAYON+: activado, omitir, cuándo reproducirlo; garantizar que **se vea el vídeo**, no solo audio ni splash recortado.
3. Comportamiento al salir: salir, confirmar o volver al Home.
4. Restaurar navegación/sesión anterior.
5. Idioma automático o manual.
6. Notificaciones y avisos globales.
7. Historial y contenidos recientes.
8. Recuperar estado al volver desde otra aplicación.
9. **Icono STAYON+**: elegir diseños oficiales, vista previa del launcher, restaurar, nuevas variantes mediante actualización (solo si Android TV permite aliases).
10. Deportes existentes: ligas, equipos favoritos, marcadores, alertas y recordatorios.
11. Restablecer preferencias generales sin tocar fuentes, editor, favoritos ni Home.

## 2. Fuentes

### 2.1 Canales de TV → TDT

- Elegir fuente/sintonizador Android TV/TCL; importar canales YA sintonizados por el televisor, no prometer escaneo RF propio.
- Actualizar manual y automáticamente la importación, consultar estado/sintonizador.
- Eliminar fuente de STAYON+ sin borrar la sintonización del TCL.
- Servicios TDT de audio `SERVICE_TYPE_AUDIO` van a **Radios TDT**; hoy el sync los omite y necesita implementación.

### 2.2 Canales de TV → IPTV

- Gestionar fuentes/listas M3U, Xtream Codes, Stalker; añadir/editar URL, usuario, contraseña y ajustes avanzados.
- Configurar desde móvil, probar conexión, fuente predeterminada.
- Actualización automática, sincronización normal/limpia, importar categorías TV/películas/series, eliminar.
- **No** clasificar radios a partir de listas IPTV de TV: las URLs de IPTV Radio son completamente **independientes**.

### 2.3 Radio

- Radios TDT: fuente del mismo sintonizador, solo audio, importar, actualizar, estado, eliminar solo en STAYON+.
- Radios IPTV: listas M3U propias de Radio (URL independiente), configurar desde móvil, editar, probar, actualizar, eliminar.
- **Añadir emisora individual**: nombre, URL directa de streaming y logo opcional.

### 2.4 EPG TV / EPG Radio

- Fuentes XMLTV, native TDT y EPG de proveedores IPTV; añadir/editar/eliminar; enlaces XMLTV y `.xml.gz`.
- Refresco manual y automático (desactivado, inicio, cada N horas/días, etc.), estado y errores; desplazamiento horario, días retenidos, recordatorios TV, catch-up.
- **Prioridad independiente por fuente de canales** (cada fuente TDT/lista IPTV tiene lista ordenada de una o más fuentes EPG). Rellenar huecos con segundo/tercer EPG; sin descargar varias veces el mismo feed compartido.
- Asignación automática por `tvg-id`/nombre, manual con override persistente; EPG Radio por separado.
- Cada fuente EPG tiene toggle **Usar logos de esta fuente EPG**, que habilita la obtención de logos pero **no** establece prioridad (eso pertenece a Picons).
- **Imágenes universales de programas**: para todo XMLTV que las incluya (no hardcode EPG_dobleM), leer `programme/icon`, `programme/image` y variantes compatibles. Conservar URL asociada a programa/canal/horario; validar esquema HTTP(S), tamaño/caché; mostrar en vista previa del canal, guía y fichas.
- Fanart 16:9, póster 2:3, automático; fuentes de imágenes prioritarias; completar faltantes con TMDB/TheTVDB mediante matching fiable, nunca sustituir una imagen correcta por una errónea.
- EPG_dobleM es el proveedor que usa el usuario; deben funcionar XMLTV/archivos comprimidos de ese proveedor y cualquier otro que tenga imágenes. Evitar scraping HTML cuando XMLTV suministre las URLs.
- EPG Radio: sus fuentes, prioridades, matching, estado y logos; sin inventar recordatorios/catch-up radio.
- Apariencia de guía: ancho columna canal, ancho temporal, restaurar.

### 2.5 Editor de canales

- Editor unificado TDT/IPTV + radio, filtros origen/tipo/favoritos, búsqueda, selección múltiple.
- Renombrar, sustituir o quitar prefijos/sufijos, ordenar, mover en bloque, ocultar/mostrar, favoritos, deshacer/rehacer, guardar/revisar; preservar overrides tras refresh.
- **Agrupar duplicados** (La 1 TDT, IPTV FHD, IPTV HD, etc.) automáticamente con corrección/separación manual. Prioridad predeterminada TDT y, si falla, IPTV por calidad; permitir override por canal.
- Asignación manual EPG por canal; búsqueda por ID/nombre, candidatos automáticos y EPG elegida, reset automático, offset individual.
- Modo **asignación rápida por lotes con mando**: arriba/abajo elige canal; OK abre búsqueda/selección EPG; al aceptar autoguarda y pasa al siguiente; marcar asignado/no asignado/dudoso, omitir y volver.
- Acceso directo a asignación manual de logo.

### 2.6 Logos (Picons)

- Fuentes TDT/IPTV, feeds XMLTV habilitados y bibliotecas externas; colecciones SVG/PNG (p. ej. Wikimedia).
- Prioridad configurable: logo personalizado → colección Picons → logos XMLTV habilitados → original TDT/IPTV; orden editable.
- Matching por ID/nombre, asignación manual e importación propia, pendientes sin identificar, actualizar, caché, restaurar.
- Respetar proporciones, transparencia y logos centrados. Tamaño **visual** de logos va en Apariencia; no mezclar prioridades de Picons con prioridades de programación EPG.

### 2.7 Metadatos

- TMDB, TheTVDB, Trakt y fuentes compatibles: API/credenciales, idiomas, datos de películas/series/episodios, carátulas, sinopsis, reparto, valoraciones; test de conexión.

## 3. Home

- Organización y visibilidad del menú superior (TV, Radio, Música, Apps y demás secciones disponibles).
- Widgets: añadir, quitar, mover, cambiar tamaño por widget y contenido (último canal, favoritas, servicios musicales), accesos rápidos.
- **Editar directamente desde Home** con mando: mantener OK → modo edición; flechas mueven; cambio de tamaño; guardar/cancelar; no interferir con pulsación OK normal.
- **Escala de toda la UI del Home** (aproximadamente 70–130%, definir valores finales): navegación, textos, iconos, márgenes, espaciado; vista previa y restaurar. Independiente del tamaño individual de widgets.
- Pantalla predeterminada / restaurar Home. Continuar viendo, tendencias, vista previa y widgets de apps/música cuando tengan datos reales.
- Android TV Home: gestionar integración/publicación existente.
- Pendiente de decisión explícita: varias plantillas, widgets dinámicos avanzados, PiP en Home, backup específico, comportamiento Home y reloj/fecha/tiempo; no marcar como completados por inferencia.

## 4. Reproducción

1. **Reproductores**: TV, por lista IPTV, películas/series, externo, reset de elección; TDT nativo cuando sea necesario.
2. **Imagen/vídeo**: hardware/software decode, calidad, HDR/SDR, AFR, resolución, zoom/aspect ratio, tunneled.
3. **Audio/subtítulos**: volumen, surround, passthrough, nivelación, nocturno, retrasos, idiomas, estilo. Recordar pista de audio/subtítulos **por canal**. OpenSubtitles, cuenta, búsqueda/descarga y borrado.
4. **TV en directo/IPTV**: vista previa de vídeo/imagen EPG, audio de preview, numeración, latencia, preroll, timeout, búfer y reconexiones por lista.
5. **Timeshift/LOVEStv**: activación, duración/resume, saltos, regresar al directo, botón verde para saltar anuncios, intervalos de avance x2/x4/x8/x16/x32; botón azul y HbbTV. Preservar LOVEStv que funciona en TCL, no introducir sesiones que bloqueen el mando ni pantalla negra.
6. **Controles/multivista**: saltos, flechas, PiP/mini player, Multiview y máximo de ventanas.
7. **Grabación/continuidad/descargas**: grabar canal actual, reserva conexión, pre/post-roll, reanudar, autoplay siguiente, opciones cuando la descarga está en curso.
8. **Mejoras aprobadas**: cambio automático de fuente al fallar; recuperación automática sin negros prolongados; gestión avanzada LOVEStv; memoria audio/subtítulos por canal; diagnóstico técnico (codec, bitrate, FPS, resolución, buffer, errores); excepciones individuales por canal.

## 5. Apariencia e interfaz

1. Tema/colores y fondos (negro puro predeterminado), temas descargables, efecto opcional existente — pero **el diseño base elegido excluye cristal**.
2. Escala de otras interfaces, independientemente del Home.
3. Logos: tamaño, centrado, proporciones, transparencias, fondos.
4. Listas y navegación: filas, paneles, cantidad visible, memoria de posición/categoría.
5. Barra del reproductor: tamaño, posición, campos, desaparición tras **15 s** configurable, ocultar con Atrás.
6. Fuente/tamaño/grosor.
7. Focus y animaciones, velocidades y transiciones.
8. Reloj, fecha y tiempo: ubicación/unidades/colores si están habilitados.
9. Restablecer apariencia.

## 6. Mando y navegación

- Personalizar OK, Atrás, Home si Android lo permite, flechas, botones rojo/verde/azul/amarillo y teclas multimedia.
- Pulsación corta/larga/doble cuando exista soporte.
- Cambio de canales, lista, canal anterior, menú del reproductor y gestión del foco.
- Tiempo de repetición, scroll, cierre automático, teclados Bluetooth/números directos, restaurar.
- **Captura interactiva de teclas** al pulsar físicamente en el mando, solo si Android entrega esos eventos.
- Orden de acciones de menús contextuales de TV, películas, series y episodios.

## 7. Música y servicios

- Spotify, YouTube Music, Amazon Music y futuros servicios compatibles.
- Vincular/desvincular cuentas cuando el proveedor ofrezca API/permiso.
- Control interno de reproducción, dispositivo de salida/Connect, carátulas, progreso y controles.
- Audio en segundo plano y servicio predeterminado.
- No fingir integración integrada donde solo exista abrir aplicación o widget visual.

## 8. Aplicaciones

- Aplicaciones instaladas: abrir, mostrar/ocultar, orden manual/alfabético/uso, favoritos y accesos en Home.
- Detectar nuevas instalaciones, identificar apps no optimizadas para Android TV y ocultarlas por defecto con opción de mostrar.
- Acceder a información/permisos/desinstalación mediante las pantallas nativas autorizadas.
- Restaurar distribución.

## 9. Sistema y mantenimiento

1. Información/versión/licencias.
2. Actualizaciones GitHub desde la app, búsqueda automática/manual, novedades y descarga correcta.
3. Almacenamiento, carpeta de descarga, cachés EPG/imágenes/vídeo y limpieza selectiva **separando caché de datos del usuario**.
4. Backup/restore de listas, asignaciones, favoritos, historial, Home y ajustes; cifrado si procede.
5. **Sincronización entre televisores**: enviar, recibir, combinar, emparejar.
6. Red, proxy, DNS, diagnóstico.
7. Región/zona horaria/formato; idioma app ya está en General (sin duplicar).
8. Permisos/TDT/compatibilidad.
9. Logs/export y diagnóstico de TDT/IPTV/LOVEStv, con datos técnicos de reproducción.
10. STAYON+ como launcher, inicio, tecla Home según posibilidades TCL/Android TV.
11. Restauración por apartado y general, con confirmación.

## Publicación y seguridad de la actualización

- La firma de una APK de actualización **debe ser la misma que usa la versión instalada**. No usar el keystore desechable de CI para publicar.
- Conservar `applicationId`, preferencias locales, bases de datos y rutas de configuración; migrar esquema si se añaden campos a EPG.
- Subir la versión por encima de **5.4.3** / `versionCode=101003` solo cuando realmente se vaya a publicar y exista un artefacto firmado verificable.
- El actual workflow `.github/workflows/channelplus-check.yml` ejecuta tests y genera APK de pruebas, **no** publica APK firmada compatible con la versión instalada.
- Pruebas obligatorias: navegación con mando / foco / TV native settings, continuidad TV/IPTV, logos, EPG XMLTV+GZIP, caché, radio TDT, backup, TIF native timeshift, LOVEStv primera entrada y entradas repetidas, retorno al directo, actualización in-app.
- No cerrar como «todo implementado» ni subir release sin cumplir estos criterios.
