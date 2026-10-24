# GIF: diagnóstico y optimización (corpus sintético)

Equipo: motorola edge 30 pro, heap máximo Android 512 MiB. Las pruebas instrumentadas generan JPEG a partir de gradientes RGB; **no** son descargas de Pixiv ni una evaluación visual de una obra real. El ZIP y los GIF se escriben en caché durante los benchmarks; la aplicación sigue guardando descargas en la carpeta SAF elegida. El codificador corre en el ejecutor de `DownloadService`, no en el hilo principal. Se usó `System.nanoTime()` para medir cada fase de una copia *solo de prueba* del código fuente de Square gifencoder 0.10.1 (`app/src/androidTest/java/com/pixload/bench/square/`).

## Fase 0 — Square original, JPEG 960 × 540, un fotograma

| Fase | Tiempo observado |
| --- | ---: |
| Descompresión ZIP y decodificación JPEG | 4–24 ms |
| Escalado (la fuente ya es 960 × 540) | 0 ms |
| `Bitmap.getPixels()` | ~1 ms |
| `int[]` → objetos `Color[][]` de Square | 69–258 ms |
| Histograma de colores | 228–279 ms |
| Cuantización median-cut | 3.40–3.48 s |
| Dithering Floyd–Steinberg | **25.8–29.9 s** |
| Índices de paleta | 113–143 ms |
| LZW | 544–656 ms |
| Escritura en `OutputStream` | ~1 ms |
| Codificación total | **30.4–34.5 s** |

**Dominante: dithering (~85–87 %)**; en Square el dither busca linealmente entre hasta 256 colores por píxel y crea numerosos objetos. Medida de asignaciones del runtime ART (`art.gc.bytes-allocated`): **4 590 854 144 bytes (~4,28 GiB) en un fotograma de 960 × 540**. El contador `Debug.getThreadAllocSize()` es un `int` que se desborda y NO sirve por sí solo para esta cifra. ΔPSS de una ejecución: ~55–89 MiB; **no** es memoria pico. GIF resultante: 175 613 bytes. Medidas de ejecuciones distintas, afectadas por calentamiento y GC.

## Fase 1 — comparación de cambios aislados

Un fotograma JPEG con gradiente; MAE = error absoluto RGB medio de píxeles muestreados, medido contra el JPEG decodificado a la misma resolución. Menor MAE no garantiza mejor calidad perceptual ni menos parpadeo. Tiempos de ejecución única/variación por calentamiento; comparar con prudencia.

| Cambio respecto a referencia | Tiempo de codificación (+ preparación) | GIF | MAE | Resultado |
| --- | ---: | ---: | ---: | --- |
| Square median-cut + Floyd, 960 × 540 | 30.4 s | 175 613 B | 4,78 | Referencia |
| **Solo reducir a 480 × 270** antes del encoder (misma cuantización/dithering) | 8,93 s (otra corrida 9,37 s), **~3,4×** | 54 372 B | 4,72 | Menos píxeles que procesar; ImageDecoder usa tamaño objetivo, sin bitmap a resolución completa |
| **Solo** cambiar a UniformQuantizer a 960, mantener Floyd | 26,49 s (**~13 %** menos) | 207 702 B | 15,97 | Descartado para modo equilibrado/calidad por pérdida cromática |
| **Solo** desactivar difusión usando `NearestColorDitherer` de Square a 960 | 33,73 s (**11 % más**) | 41 530 B | 3,61 | Descartado: sigue buscando linealmente en 256 colores y el MAE no mide banding |
| Igual pero a 480 | 9,93 s vs 8,93 s (**11 % más**) | 19 533 B | 3,66 | Descartado |
| 480 + **paleta global muestreada median-cut + LUT 5 bits/canal**, sin difusión | 0,53 s paleta + 0,69 s encoder (otra corrida ~0,11 + 0,33 s) | 19 840 B | 4,15 | Preset rápido usa Uniform + LUT sin difusión; los resultados indicados son con median-cut |
| 480 + **paleta global median-cut + LUT y Floyd en buffers por filas** | 0,11–0,30 s paleta + 0,33–0,72 s encoder; **~9–20×** frente a 8,93 s | 56–57 KB | ~5,36 | Adoptado en Equilibrado; elimina la búsqueda lineal dominante |

La paleta global se construye de muestras de **3 frames** (primero, central, último; 5 en Calidad), no se cargan todos los frames. Su reutilización evita cuantización por fotograma y puede reducir parpadeo, pero esto último requiere inspección visual. Se reutiliza `int[]` para `Bitmap.getPixels` y se reciclan `Bitmap` tras usarlos; el proyecto anterior ya reutilizaba parte de estos buffers, así que **no se atribuye una ganancia temporal aislada a esa medida**. En el fotograma 480 × 270, ART contabilizó ~1 165 881 344 B asignados con Square/Floyd original y ~29 679 616 B con paleta+LUT/Floyd (~39× menos, efecto combinado, no solo reutilización de `int[]`).

### Antes vs después, mismo corpus de 10 JPEG cambiantes de 480 × 270

| Métrica | Antes (Square median-cut/Floyd por frame) | Después (Equilibrado, paleta+LUT) |
| --- | ---: | ---: |
| Tiempo total (incluye JPEG/ZIP/GIF) | 86,60 s | 3,35 s (**~25,9×**) |
| Tamaño GIF | 531 506 B | 590 306 B (**+11,1 %**) |
| Pico PSS muestreado cada 20 ms | 138 440 KiB | 116 397 KiB (**−15,9 %**) |
| Pico de tamaño de heap Java muestreado | 57 824 KiB | 67 474 KiB (**+16,7 %; no mejora garantizada**) |

Estas dos mediciones comparables se ejecutaron en procesos de instrumentación separados; una corrida anterior en el mismo proceso tras otros benchmarks dio 122,00 s/3,47 s y PSS 140 359/101 730 KiB respectivamente. La memoria varía con el GC, la inicialización de Kotlin/ART y el orden de pruebas: **no** se garantiza menor heap en cada ejecución. Los picos son muestras a intervalos y pueden omitir picos más breves. Se midió el corpus completo de **100 JPEG** solo con la versión nueva: **30,18 s** (otra corrida 22,14 s), GIF **5 706 288 B**, PSS máximo muestreado **162 455–173 762 KiB**, heap máximo **73 250–82 322 KiB**. No se extrapolan tamaño ni memoria de la versión vieja a 100 fotogramas: no se ejecutó ese experimento completo. Criterio acordado: ≤3 min para 100 frames a 480 px; **cumplido**. Por eso no se activa Fase 2 (encoder propio/NDK). No se ha evaluado ni verificado `android-ndk-gif` para AGP/NDK ni páginas de 16 KB porque la condición de entrada a Fase 2 no se cumplió.

## Presets disponibles en la vista de ugoira

| Preset | Ancho máximo | Paleta/quantizer | Dithering | FPS máximo |
| --- | ---: | --- | --- | ---: |
| Rápido | 360 px | Uniform, compartida por todos los frames | Sin difusión; LUT 5 bits/canal | 15 |
| Equilibrado (por defecto) | 480 px | Median-cut de 3 frames muestreados, compartida | Floyd–Steinberg con LUT 5 bits/canal | 24 |
| Calidad | 720 px | Median-cut de 5 frames muestreados, compartida | Floyd–Steinberg con LUT 5 bits/canal | 30 |

El límite de fps descarta imágenes intermedias cuando es necesario, pero el retardo GIF se calcula a partir del **tiempo acumulado original** y se redondea a centésimas en cada imagen escrita (el bucle es infinito). El progreso reporta frames originales procesados / total; cancelar se comprueba entre frames, leyendo JPEG y por fila en la difusión.

## Riesgos y casos límite

- **Miles de frames**: muchas lecturas de ZIP y GIFs grandes; no se retienen todos los bitmaps, pero Square sigue creando objetos `Color` para cada frame y la CPU/GC puede aumentar notablemente.
- **Poca RAM**: JPEG comprimido se lee en un array acotado a 40 MiB para `ImageDecoder` (Android 28+), más bitmap, buffer de píxeles, tabla y estructuras de Square; el PSS del equipo de prueba no garantiza caber en otros teléfonos. En Android 26–27 se usa `BitmapFactory.inSampleSize` (a veces reduce más de lo solicitado para no decodificar primero a tamaño completo).
- **Delays bajos**: GIF ofrece centésimas, no milisegundos. Los frames que exceden el límite FPS se descartan manteniendo duración acumulada; si la duración total es <10 ms, un GIF con al menos una imagen necesita un retardo mínimo y puede existir desfase inevitable. Un único frame con delay >655 350 ms excede el campo GIF de 16 bits y se trunca.
- **Calidad**: RGB con 256 colores por frame, sin transparencia en Square; una paleta global puede perder colores de escenas no presentes en los frames muestreados. LUT de 5 bits/canal aproxima vecino más cercano y puede crear contornos en gradientes. MAE sintético no sustituye la comparación visual de obras Pixiv en el teléfono del usuario.
- **Cancelación**: el LZW de terceros no expone interrupción dentro del fotograma; se atiende al concluirlo. El resultado incompleto SAF no se expone como descarga completada; revisar residuos si se interrumpe el proceso.

Reproducción: `./gradlew :app:assembleDebugAndroidTest`, instalar ambas APK solo si `DownloadService` está inactivo y ejecutar `adb shell am instrument -w -r com.pixload.app.test/androidx.test.runner.AndroidJUnitRunner`. Para pruebas aisladas: `-e class com.pixload.app.GifBaselineProfileTest` y `-e class com.pixload.app.GifPhase1BenchmarkTest`.
