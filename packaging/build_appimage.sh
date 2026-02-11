#!/bin/bash
set -e

APP_NAME="DataForge"
VERSION="1.0.0"
APPDIR="$APP_NAME.AppDir"

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

FRONTEND_DIR="$ROOT_DIR/frontend"
BACKEND_DIR="$ROOT_DIR/backend"
PACKAGING_DIR="$ROOT_DIR/packaging"

echo "=============================="
echo "Building $APP_NAME v$VERSION"
echo "=============================="

# 🧹 Limpiar builds anteriores
rm -rf "$ROOT_DIR/$APPDIR"
rm -f "$ROOT_DIR/$APP_NAME"-x86_64.AppImage

# ===============================
# 🔹 1. Compilar backend
# ===============================
echo "Compilando backend..."
cd "$BACKEND_DIR"
mvn clean package -DskipTests

JAR_FILE=$(ls "$BACKEND_DIR"/target/*.jar | head -n 1)

# ===============================
# 🔹 2. Compilar frontend
# ===============================
echo "Compilando frontend..."
cd "$FRONTEND_DIR"
flutter build linux --release

# ===============================
# 🔹 3. Crear estructura AppDir
# ===============================
cd "$ROOT_DIR"

mkdir -p "$APPDIR"
mkdir -p "$APPDIR/backend"
mkdir -p "$APPDIR/frontend"
mkdir -p "$APPDIR/runtime"

# Copiar AppRun y desktop
cp "$PACKAGING_DIR/AppRun" "$APPDIR/"
cp "$PACKAGING_DIR/dataforge.desktop" "$APPDIR/"
cp "$PACKAGING_DIR/dataforge.png" "$APPDIR/"

chmod +x "$APPDIR/AppRun"

# ===============================
# 🔹 4. Copiar backend
# ===============================
echo "Copiando backend..."
cp "$JAR_FILE" "$APPDIR/backend/Dataforge.jar"

# ===============================
# 🔹 5. Copiar frontend
# ===============================
echo "Copiando frontend..."
cp -r "$FRONTEND_DIR/build/linux/x64/release/bundle/"* "$APPDIR/frontend/"
chmod +x "$APPDIR/frontend/data_forge_studio"

# ===============================
# 🔹 6. Copiar JRE
# ===============================
echo "Copiando JRE..."
cp -r "$PACKAGING_DIR/jre" "$APPDIR/runtime/"

# ===============================
# 🔹 7. Generar AppImage
# ===============================
echo "Generando AppImage..."
appimagetool "$APPDIR"

echo "=============================="
echo "AppImage creada correctamente"
echo "=============================="
