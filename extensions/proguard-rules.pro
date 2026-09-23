-dontobfuscate
-dontoptimize
-keepattributes *
# Also keeps the generated protobuf messages, whose fields protobuf lite reads by reflection.
-keep class io.github.leobenzol.** {
  *;
}
# R8 can strip Kotlin intrinsics methods that are used by extension Kotlin code.
-keep class kotlin.jvm.internal.Intrinsics {
    public static *;
}
