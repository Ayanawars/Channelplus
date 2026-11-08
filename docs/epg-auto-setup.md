# STAYON+ — EPG automático (TDT e IPTV)

## Flujo para usuarios

1. Al importar una lista IPTV que anuncia una URL XMLTV / Xtream / portal compatible,
   STAYON+ registra esa URL como fuente EPG (si aún no existe) y la sincroniza
   mediante WorkManager, sin pedir una segunda vinculación manual.
2. Las fuentes EPG explícitas se añaden en **Configuración → Fuentes → EPG TV**.
   Fuentes nuevas y fuentes antiguas sin preferencia guardada: actualización cada
   24 horas. Una fuente con actualización establecida explícitamente en «Desactivada»
   mantiene ese valor.
3. Al terminar una sincronización EPG, se preservan las asociaciones por `tvg-id`
   que ya tengan programas. Para canales TDT/IPTV restantes se buscan coincidencias
   de nombre con umbral de confianza, de forma no bloqueante y en segundo plano.
4. En **Configuración → Fuentes → EPG TV → Buscar EPG automáticamente** se puede
   repetir el proceso. Si todavía no hay datos, se solicita descargar los feeds.
5. Para corregir excepciones, entrar en **Asignar EPG manualmente**: elegir canal,
   buscar la entrada XMLTV correcta, continuar con el siguiente y pulsar **Guardar**
   al final. Restaurar automático elimina la corrección manual.
6. **Canales sin programación** abre el editor filtrado por cobertura de la guía
   descargada. Distingue entre una guía sin asignar y un ID sin próximos programas.
7. **Prioridad de EPG por lista** permite ordenar los feeds XMLTV para una lista IPTV o
   fuente de antena. La guía usa el primer feed con datos para el ID efectivo y la
   ventana solicitada. Si ninguno tiene datos, vuelve a la guía compartida.

## Detalles de integridad

- Las guías XMLTV son compartidas entre canales importados TDT e IPTV; el enlace
  individual se almacena como un override estable de canal, sin cambiar su stream.
- El auto-match nunca sustituye un override manual; además, la escritura verifica
  los overrides actuales al confirmar, por si se editaron mientras corría el análisis.
- Solo las coincidencias con programas y alta confianza se aplican sin preguntar.
  Las dudosas se reservan para revisión manual.
- Las sincronizaciones EPG deben ejecutarse en WorkManager; la actualización automática
  respeta el estado de reproducción cuando se ejecuta en segundo plano.
- El EPG nativo de un sintonizador o proveedor puede ser suficiente sin añadir una URL
  XMLTV independiente. No se debe mostrar el estado vacío si existen programas.
- No se borra la configuración anterior, ni favoritos, ni logos, ni ajustes de LOVEStv.

## Limitaciones y pruebas

- Las asignaciones ya guardadas se conservan. Si dos guías usan IDs diferentes,
  restaura la asignación automática del canal para buscar de nuevo con el orden actual.
- Las listas IPTV enormes se pueden filtrar por búsqueda en la asignación manual.
  El listado inicial no intenta materializar decenas de miles de filas a la vez.
- Pruebas necesarias en TCL: TDT sin `tvg-id`, M3U con `tvg-id`, M3U con
  `url-tvg`, Xtream, XMLTV GZIP, guía existente de EPG_dobleM, nombre ambiguo,
  guía incompleta, refresco, preservación de overrides, navegación y botón Atrás.
- Sin prueba funcional en televisión no debe anunciarse el EPG como reparado al 100 %.
