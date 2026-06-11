# chesslib
-keep class com.github.bhlangonijr.chesslib.** { *; }

# LiteRT-LM (JNI bindings)
-keep class com.google.ai.edge.litertlm.** { *; }

# Firestore model classes (reflection-based mapping)
-keep class com.chartmann.knightfall.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# WorkManager — Room generates WorkDatabase_Impl via annotation processor;
# R8 strips the no-arg constructor because it's only invoked by reflection.
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(); }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
