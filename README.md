# MisGastos


## Integrantes
- Leandro Javier Monzón
- Alan Leonel Estrella

## Descripción
MisGastos es una aplicación Android para registrar, consultar y administrar gastos personales.

La app permite:
- Registro e inicio de sesión con Firebase Authentication.
- CRUD de gastos.
- Categorización de gastos.
- Carga de comprobantes como imagen o PDF.
- Lectura automática de comprobantes mediante OCR para sugerir monto, categoría y descripción.
- Visualización de detalle del gasto.
- Historial de gastos con filtros.
- Exportación de reportes en PDF.
- Configuración de umbral mensual y notificaciones.
- Soporte multilenguaje español/inglés/Portugues.

## Tecnologías utilizadas
- Android Studio
- Kotlin
- Firebase Authentication
- Cloud Firestore
- Firebase Storage
- ML Kit Text Recognition
- Google Maps / ubicación
- Material Components

## Configuración del proyecto

### 1. Clonar el repositorio

```bash
git clone https://github.com/LeonelEstrella/MisGastos

## Configuración de Firebase

Por motivos de seguridad, el archivo `google-services.json` no se encuentra incluido en este repositorio.

Para ejecutar correctamente la aplicación es necesario configurar Firebase y agregar dicho archivo manualmente.

### Pasos para obtener el archivo

1. Ingresar a Firebase Console.
2. Crear un proyecto Firebase o utilizar uno existente.
3. Registrar una aplicación Android con el package name:

```text
com.catedra.misgastos
```

4. Descargar el archivo `google-services.json`.
5. Copiar el archivo descargado dentro de la carpeta:

```text
app/google-services.json
```

### Servicios necesarios

La aplicación requiere habilitar los siguientes servicios de Firebase:

* Firebase Authentication (Email y Contraseña)
* Cloud Firestore
* Firebase Storage


