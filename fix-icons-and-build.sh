#!/data/data/com.termux/files/usr/bin/bash

echo "🎨 Création des icônes manquantes..."

# Créer les dossiers mipmap
mkdir -p ~/claudeapk/TermuxDevCenter/app/src/main/res/mipmap-hdpi
mkdir -p ~/claudeapk/TermuxDevCenter/app/src/main/res/mipmap-mdpi
mkdir -p ~/claudeapk/TermuxDevCenter/app/src/main/res/mipmap-xhdpi
mkdir -p ~/claudeapk/TermuxDevCenter/app/src/main/res/mipmap-xxhdpi
mkdir -p ~/claudeapk/TermuxDevCenter/app/src/main/res/mipmap-xxxhdpi
mkdir -p ~/claudeapk/TermuxDevCenter/app/src/main/res/mipmap-anydpi-v26

# Créer des icônes PNG simples avec ImageMagick ou en copiant une icône système
# Si ImageMagick n'est pas installé, on va créer un lien symbolique vers une icône système

# Essayer de trouver une icône système à utiliser comme placeholder
if [ -f "/system/framework/framework-res.apk" ]; then
    echo "📱 Utilisation d'icônes système comme placeholder..."
    
    # Créer des icônes PNG basiques en couleur unie (méthode de secours)
    # On va utiliser les outils Termux disponibles
    
    for dir in hdpi mdpi xhdpi xxhdpi xxxhdpi; do
        touch ~/claudeapk/TermuxDevCenter/app/src/main/res/mipmap-$dir/ic_launcher.png
        touch ~/claudeapk/TermuxDevCenter/app/src/main/res/mipmap-$dir/ic_launcher_round.png
    done
fi

# Simplifier: utiliser seulement des icônes vectorielles (XML)
cat > ~/claudeapk/TermuxDevCenter/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
EOF

cat > ~/claudeapk/TermuxDevCenter/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
EOF

# Créer le drawable foreground
mkdir -p ~/claudeapk/TermuxDevCenter/app/src/main/res/drawable

cat > ~/claudeapk/TermuxDevCenter/app/src/main/res/drawable/ic_launcher_foreground.xml << 'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
  <group android:scaleX="0.5"
      android:scaleY="0.5"
      android:translateX="27"
      android:translateY="27">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M20,20 L88,20 L88,88 L20,88 Z"/>
    <path
        android:fillColor="#2196F3"
        android:pathData="M30,30 L78,30 L78,36 L30,36 Z"/>
    <path
        android:fillColor="#2196F3"
        android:pathData="M30,42 L78,42 L78,48 L30,48 Z"/>
    <path
        android:fillColor="#2196F3"
        android:pathData="M30,54 L78,54 L78,60 L30,60 Z"/>
    <path
        android:fillColor="#4CAF50"
        android:pathData="M30,66 L48,66 L48,72 L30,72 Z"/>
  </group>
</vector>
EOF

cat > ~/claudeapk/TermuxDevCenter/app/src/main/res/values/ic_launcher_background.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#2196F3</color>
</resources>
EOF

echo "✅ Icônes créées !"
echo ""
echo "🚀 Lancement de la compilation..."
cd ~/claudeapk/TermuxDevCenter
gradle assembleDebug
