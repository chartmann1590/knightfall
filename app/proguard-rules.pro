# chesslib
-keep class com.github.bhlangonijr.chesslib.** { *; }

# LiteRT-LM (JNI bindings)
-keep class com.google.ai.edge.litertlm.** { *; }

# Firestore model classes (reflection-based mapping)
-keep class com.chartmann.knightfall.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
