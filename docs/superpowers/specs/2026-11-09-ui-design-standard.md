# Estándar de diseño de UI — Cabina de Fotos

**Fecha:** 2026-11-09 · **Estado:** vigente · **Aplica a:** todo `app/src/main/java/com/photobooth/kiosk/ui/`

Este documento es la fuente de verdad para diseñar o modificar cualquier pantalla. Antes de crear un botón, una tarjeta o un paso nuevo, revisa aquí qué componente usar. Si algo no encaja, primero se actualiza este estándar y luego el código, nunca al revés.

## 1. Por qué existe

Antes de este estándar el análisis encontró:

- **Admin:** cada pantalla guardaba distinto. Los switches, chips de rotación y el modelo de IA se guardaban al tocarlos, sin confirmación. La URL tenía su propio botón y el editor de marca otro al final. El modelo de IA "no se guardaba": el modo IA nunca volvía a leer el modelo guardado y el guardado se ignoraba en silencio si quedaba un resultado IA de invitado en memoria.
- **IA vs Collage:** el botón de captura vivía dentro del contenido con alturas de 72, 64, 60 y 56 dp y radios de 18 y 20 dp. En IA había dos botones azules a la vez ("Tomar foto" y "Continuar"). En Collage la barra inferior se ocultaba hasta tener 3 fotos. Cada flujo tenía su propio `SectionTitle`.
- **Pantalla de generación:** fondo plano, un punto en el borde y tres partículas. Se sentía vacía.

## 2. Principios

1. **Una sola acción azul por pantalla.** `SyscomBlue` es la acción principal y solo hay una visible. En los flujos de invitado vive en la barra inferior. En Admin es "Guardar cambios".
2. **Misma estructura, mismo lugar.** El invitado y el operador siempre encuentran la acción principal abajo a la derecha y el regreso abajo a la izquierda (invitado) o arriba a la izquierda (admin).
3. **Nada se guarda sin que el operador lo pida** en pantallas de ajustes de Admin. Las acciones sobre elementos de una lista (borrar, importar, renombrar, activar) sí son inmediatas y destructivas con confirmación.
4. **Los tamaños viven en un solo lugar:** `BoothDimens` (invitado) y `AdminScreenScaffold.kt` (admin). No se escriben alturas ni radios sueltos en una pantalla.
5. **Textos en ES y EN.** Todo texto nuevo va en `values/strings.xml` y `values-en/strings.xml`.

## 3. Paleta y jerarquía de botones

| Nivel | Componente (invitado) | Color | Uso |
|---|---|---|---|
| Primario | `BoothPrimaryButton` | `SyscomBlue`, texto blanco | Continuar, Tomar foto, Generar, Terminar. Solo uno visible. |
| Secundario fuerte | `BoothSecondaryButton` | `SyscomTeal`, texto `Ink` | La acción secundaria que merece peso: "Repetir foto", "Repetir esta foto". |
| Secundario | `BoothOutlinedButton` | Borde `Cream` 38 %, texto `Cream` | Regresar, Salir, Ver foto, Repetir las 3, Cancelar. **Nunca azul.** |
| Terciario | `TextButton` | `Cream` o `SyscomBlueLight` | Enlaces menores (copiar liga, refrescar). |
| Destructivo | `TextButton` | `#FF7A7A` | Borrar, Descartar en diálogos de confirmación. |

Fondo `Ink #050B14`, superficies `InkSoft #0B1727`, texto `Cream #F3F8FF`. Estados seleccionados: borde 2 dp `SyscomBlue` + relleno `SyscomBlue` 22 %.

## 4. Flujos de invitado (IA y Collage)

Componentes en `ui/components/BoothControls.kt`. Ambos flujos los usan; no se duplican en privado.

### 4.1 Estructura de pantalla

```
┌───────────────────────────────────────┐
│ BoothFlowHeader                       │  título 22 sp · "Paso X de N · nombre" 14 sp · X para salir
│ ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬ progreso 6 dp     │
├───────────────────────────────────────┤
│ Contenido (ancho máx 560/680 dp)      │  BoothSectionTitle 22 sp + subtítulo 15 sp
│  · scroll vertical, salvo Captura     │  separación 14 dp
│  · acciones secundarias en una Row    │
├───────────────────────────────────────┤
│ BoothBottomBar                        │  [Outlined 1f] [Primario 1.4f]  · 68 dp · radio 20
└───────────────────────────────────────┘
```

### 4.2 Medidas (`BoothDimens`)

| Token | Valor |
|---|---|
| `BarButtonHeight` | 68 dp (barra inferior) |
| `InlineButtonHeight` | 60 dp (acciones dentro del contenido) |
| `ButtonRadius` | 20 dp (todos los botones) |
| `CardRadius` | 20 dp (tarjetas de estilo/fondo) |
| `PreviewRadius` | 24 dp (vista de cámara y fotos grandes) |
| Padding horizontal | 18 dp (< 600 dp) / 32 dp |

### 4.3 Barra inferior

- **Siempre visible en todos los pasos, incluida Captura.**
- Izquierda: `BoothOutlinedButton`. En el primer paso dice "Salir" y en los demás "Regresar".
- Derecha: la única acción azul. Su texto cambia según el estado del paso:
  - Captura sin foto: "Tomar foto" (IA) o "Tomar 3 fotos" (Collage).
  - Captura con foto(s): "Continuar".
  - Último paso de configuración IA: "Generar" o "Reintentar".
  - Entrega: "Terminar", que se arma tarde para evitar dobles toques.
- **Regresar se oculta solo cuando volver perdería trabajo pagado** (IA en Marco/Entrega). Collage sí permite volver de Entrega a Diseño.
- Mientras hay cuenta regresiva o procesamiento, la acción principal queda deshabilitada, no oculta.

### 4.4 Paso de captura (patrón común)

| Estado | Contenido (Row bajo la vista) | Barra inferior |
|---|---|---|
| Sin foto | solo pista "Palma o botón" / "Toca para tomar la foto" | Salir · **Tomar foto(s)** |
| IA con foto | `Secondary` "Repetir foto" + `Outlined` "Ver foto" | Salir · **Continuar** |
| Collage viendo una foto | `Secondary` "Repetir esta foto" + `Outlined` "Repetir las 3" | Salir · **Continuar** |
| Collage con 3 fotos | `Outlined` "Repetir las 3" | Salir · **Continuar** |

Los botones de la Row comparten ancho (`weight(1f)`) y miden `InlineButtonHeight`.

### 4.5 Pantalla de generación IA (`GenerationExperience.kt`)

Experiencia "metamorfosis" a pantalla completa, sin barras de progreso ni tiempos estimados:

1. **Aurora:** tres manchas radiales (`SyscomBlue`, `SyscomTeal`, `SyscomBlueDeep`) que se mueven lento (14 s).
2. **Chispas:** 34 partículas que suben y se desvanecen en los bordes (`GenerationSparkles`, ciclo sin corte).
3. **Cometas de borde:** dos cometas con estela recorren el borde de la pantalla (`GenerationCircuit`).
4. **Tarjeta 3:4:** la foto del invitado con un anillo de luz giratorio, un haz holográfico que la escanea y la miniatura del estilo elegido que aparece y se desvanece encima.
5. **Texto mínimo:** chip "Estilo · nombre", un título y una frase corta que rota cada 2.6 s. La frase se queda en la última, no vuelve a empezar.
6. **Revelado:** al llegar el resultado, destello blanco, la tarjeta cambia al resultado, el título dice "¡Lista!" y en 1.8 s el flujo pasa a Marco.
7. Cancelar es `BoothOutlinedButton`; nunca azul.

Las animaciones se leen dentro de `Canvas`/`graphicsLayer` para no recomponer todo el árbol en cada frame.

## 5. Modo administrador

### 5.1 Estructura

```
┌───────────────────────────────────────┐
│ AdminHeader  ← título/subtítulo   ✕   │  72 dp
├───────────────────────────────────────┤
│ AdminScreenColumn                     │  LazyColumn · top 12 · bottom 28 · tarjetas a 16 dp
│  AdminCard(título 18 sp, subtítulo)   │  radio 20 · InkSoft · borde Cream 8 %
│  AdminCard …                          │
├───────────────────────────────────────┤
│ Barra de guardado (solo ajustes)      │  estado · [Descartar] [Guardar cambios]
└───────────────────────────────────────┘
```

Ancho máximo 1100 dp, padding 18 / 26 dp. El snackbar se muestra arriba de la barra de guardado.

### 5.2 Dos tipos de pantalla

| Tipo | Contenedor | Pantallas | Regla |
|---|---|---|---|
| **Ajustes** | `AdminSettingsScreen` | Cámara, Estilos, Fotos (switch), Uso IA (URL + modelo), Sistema (idioma), Editor de marca | Todo cambio va a un **borrador local**. Se guarda solo con "Guardar cambios". |
| **Gestión** | `AdminScreenColumn` | Inicio, Marcas, Fondos, Red | Acciones sobre elementos (importar, borrar, renombrar, activar) son inmediatas; las destructivas piden confirmación. |

Una pantalla de ajustes puede tener acciones inmediatas (Fotos: borrar, refrescar). Lo que es **un valor de configuración** pasa por el borrador; lo que es **una acción** no.

### 5.3 Barra de guardado

- Siempre visible en pantallas de ajustes.
- Sin cambios: texto "Todo está guardado"; "Descartar" y "Guardar cambios" deshabilitados.
- Con cambios: texto teal "Cambios sin guardar"; ambos botones habilitados.
- "Guardar cambios" es la única acción azul de la pantalla. Los demás botones de las tarjetas (Probar cámara, Probar conexión, Registrar, Elegir logo) son `OutlinedButton`.
- "Guardar cambios" escribe **solo lo que cambió** y muestra el snackbar "Cambios guardados".
- Salir con cambios pendientes (flecha, X o botón atrás) abre "¿Descartar los cambios?" con "Descartar" (rojo) y "Seguir editando".

Patrón de código:

```kotlin
var draft by remember(saved) { mutableStateOf(saved) }
AdminSettingsScreen(
    dirty = draft != saved,
    onSave = { persist(draft); onSaved() },
    onDiscard = { draft = saved },
) {
    item { AdminCard(title = …) { /* controles editan draft */ } }
}
```

### 5.4 Selector de modelo IA

- Un campo de búsqueda abre el diálogo. Elegir un modelo **solo cambia el borrador** y el campo muestra "Sin guardar · actual: X".
- Guardar usa `PhotoBoothViewModel.saveAdminModel`, que no se bloquea por una sesión de invitado en curso.
- `AiSingleViewModel.refreshModel()` vuelve a leer el modelo guardado cada vez que entra un invitado al modo IA.

## 6. Checklist antes de entregar una pantalla

- [ ] ¿Hay exactamente una acción azul visible?
- [ ] ¿Los botones salen de `BoothControls` o del scaffold de admin, sin alturas/radios sueltos?
- [ ] Invitado: ¿la barra inferior está visible y la acción principal está a la derecha?
- [ ] Admin ajustes: ¿los valores pasan por borrador y la barra de guardado se habilita solo con cambios?
- [ ] ¿Los textos nuevos están en ES y EN?
- [ ] ¿Se probó en el kiosko vertical (55") y en teléfono (< 600 dp)?
