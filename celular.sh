#!/usr/bin/env bash
set -Eeuo pipefail

# LembraAqui - compilar, instalar e abrir no celular via ADB.
# Execute na raiz do projeto:
#   chmod +x celular.sh
#   ./celular.sh

PACKAGE="com.lembraaqui.app"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

echo "========================================"
echo " LembraAqui - build e instalação Android"
echo "========================================"

# ------------------------------------------------------------
# 1. Localizar Android SDK
# ------------------------------------------------------------
SDK=""

if [[ -n "${ANDROID_SDK_ROOT:-}" && -d "${ANDROID_SDK_ROOT}" ]]; then
    SDK="${ANDROID_SDK_ROOT}"
elif [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}" ]]; then
    SDK="${ANDROID_HOME}"
else
    CANDIDATES=(
        "$HOME/Android/Sdk"
        "$HOME/Android/sdk"
        "$HOME/Library/Android/sdk"
        "/opt/android-sdk"
        "/usr/local/android-sdk"
    )

    for candidate in "${CANDIDATES[@]}"; do
        if [[ -d "$candidate" ]]; then
            SDK="$candidate"
            break
        fi
    done
fi

if [[ -z "$SDK" ]]; then
    echo
    echo "ERRO: Android SDK não encontrado."
    echo "Abra o Android Studio > Settings > Android SDK e confira o caminho."
    echo
    echo "Depois você pode executar, por exemplo:"
    echo '  export ANDROID_HOME="$HOME/Android/Sdk"'
    echo '  export ANDROID_SDK_ROOT="$ANDROID_HOME"'
    exit 1
fi

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$SDK/platform-tools:$SDK/cmdline-tools/latest/bin:$PATH"

# Cria/corrige local.properties automaticamente.
# O formato precisa escapar "\" no Windows; em Linux normalmente não há problema.
SDK_ESCAPED="${SDK//\\/\\\\}"
printf 'sdk.dir=%s\n' "$SDK_ESCAPED" > "$PROJECT_DIR/local.properties"

echo
echo "[OK] Android SDK: $SDK"

# ------------------------------------------------------------
# 2. Verificar Java
# ------------------------------------------------------------
if ! command -v java >/dev/null 2>&1; then
    echo
    echo "ERRO: Java não encontrado no PATH."
    echo "Instale/configure um JDK compatível com o Gradle/Android Studio."
    exit 1
fi

JAVA_VERSION="$(java -version 2>&1 | head -n 1)"
echo "[OK] Java: $JAVA_VERSION"

# ------------------------------------------------------------
# 3. Verificar ADB
# ------------------------------------------------------------
ADB="$SDK/platform-tools/adb"

if [[ ! -x "$ADB" ]]; then
    if command -v adb >/dev/null 2>&1; then
        ADB="$(command -v adb)"
    else
        echo
        echo "ERRO: adb não encontrado."
        echo "No Android Studio, instale 'Android SDK Platform-Tools'."
        exit 1
    fi
fi

"$ADB" start-server >/dev/null

DEVICES=()
while IFS= read -r serial; do
    [[ -n "$serial" ]] && DEVICES+=("$serial")
done < <("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')

if [[ ${#DEVICES[@]} -eq 0 ]]; then
    echo
    echo "ERRO: nenhum celular Android autorizado foi encontrado."
    echo
    echo "No celular:"
    echo "  1. Ative as Opções do desenvolvedor."
    echo "  2. Ative a Depuração USB."
    echo "  3. Conecte o cabo USB."
    echo "  4. Aceite a autorização RSA."
    echo
    echo "Saída atual do adb:"
    "$ADB" devices
    exit 1
fi

if [[ ${#DEVICES[@]} -gt 1 ]]; then
    echo
    echo "Há mais de um dispositivo conectado:"
    for i in "${!DEVICES[@]}"; do
        printf '  %d) %s\n' "$((i + 1))" "${DEVICES[$i]}"
    done

    read -r -p "Escolha o número do dispositivo: " CHOICE
    if ! [[ "$CHOICE" =~ ^[0-9]+$ ]] || (( CHOICE < 1 || CHOICE > ${#DEVICES[@]} )); then
        echo "Escolha inválida."
        exit 1
    fi
    SERIAL="${DEVICES[$((CHOICE - 1))]}"
else
    SERIAL="${DEVICES[0]}"
fi

ADB_DEVICE=("$ADB" -s "$SERIAL")
echo "[OK] Dispositivo: $SERIAL"

# ------------------------------------------------------------
# 4. Compilar
# ------------------------------------------------------------
if [[ ! -f "./gradlew" ]]; then
    echo
    echo "ERRO: gradlew não encontrado."
    echo "Coloque celular.sh na raiz do projeto LembraAqui."
    exit 1
fi

chmod +x ./gradlew

echo
echo "Compilando APK debug..."
./gradlew --console=plain assembleDebug

APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

if [[ ! -f "$APK" ]]; then
    APK="$(find "$PROJECT_DIR/app/build/outputs/apk" -type f -name '*debug*.apk' 2>/dev/null | head -n 1 || true)"
fi

if [[ -z "${APK:-}" || ! -f "$APK" ]]; then
    echo
    echo "ERRO: o Gradle terminou, mas não encontrei o APK debug."
    exit 1
fi

echo
echo "[OK] APK gerado:"
echo "     $APK"

# ------------------------------------------------------------
# 5. Instalar
# ------------------------------------------------------------
echo
echo "Instalando no celular..."
"${ADB_DEVICE[@]}" install -r -d "$APK"

# ------------------------------------------------------------
# 6. Abrir aplicativo
# ------------------------------------------------------------
echo
echo "Abrindo LembraAqui..."

"${ADB_DEVICE[@]}" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true

# Resolve automaticamente a Activity launcher.
COMPONENT="$("${ADB_DEVICE[@]}" shell cmd package resolve-activity \
    --brief -c android.intent.category.LAUNCHER "$PACKAGE" 2>/dev/null \
    | tr -d '\r' | tail -n 1 || true)"

if [[ "$COMPONENT" == "$PACKAGE/"* ]]; then
    "${ADB_DEVICE[@]}" shell am start -n "$COMPONENT" >/dev/null
else
    # Fallback caso resolve-activity não esteja disponível.
    "${ADB_DEVICE[@]}" shell monkey \
        -p "$PACKAGE" \
        -c android.intent.category.LAUNCHER \
        1 >/dev/null
fi

echo
echo "========================================"
echo " LembraAqui instalado e aberto com sucesso"
echo "========================================"
