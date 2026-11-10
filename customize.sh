#!/system/bin/sh
SKIPUNZIP=0
ui_print "- Instalando EL TRAMPERO RD"
ui_print "- Copiando librerías Zygisk..."

mkdir -p "$MODPATH/zygisk"
cp -f "$MODPATH/zygisk/arm64-v8a.so" "$MODPATH/zygisk/" 2>/dev/null
cp -f "$MODPATH/zygisk/armeabi-v7a.so" "$MODPATH/zygisk/" 2>/dev/null

ui_print "- Instalación completada. Reinicia el dispositivo."