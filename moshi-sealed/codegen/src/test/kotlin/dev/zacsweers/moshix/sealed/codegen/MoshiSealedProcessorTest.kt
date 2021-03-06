package dev.zacsweers.moshix.sealed.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import java.io.File

@KotlinPoetMetadataPreview
class MoshiSealedProcessorTest {

  @Test
  fun smokeTest() {
    val source = SourceFile.kotlin("CustomCallable.kt", """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel

      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class BaseType {
        @TypeLabel("a", ["aa"])
        class TypeA : BaseType()
        @TypeLabel("b")
        class TypeB : BaseType()
      }
    """)

    val compilation = KotlinCompilation().apply {
      sources = listOf(source)
      inheritClassPath = true
      annotationProcessors = listOf(MoshiSealedProcessor())
    }
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    val generatedSourcesDir = compilation.kaptSourceDir
    val generatedFile = File(generatedSourcesDir, "test/BaseTypeJsonAdapter.kt")
    assertThat(generatedFile.exists()).isTrue()
    //language=kotlin
    assertThat(generatedFile.readText().trim()).isEqualTo("""
      // Code generated by moshi-sealed. Do not edit.
      package test
      
      import com.squareup.moshi.JsonAdapter
      import com.squareup.moshi.JsonReader
      import com.squareup.moshi.JsonWriter
      import com.squareup.moshi.Moshi
      import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
      import kotlin.Suppress
      import kotlin.Unit
      import kotlin.collections.emptySet
      
      public class BaseTypeJsonAdapter(
        moshi: Moshi
      ) : JsonAdapter<BaseType>() {
        @Suppress("UNCHECKED_CAST")
        private val runtimeAdapter: JsonAdapter<BaseType> =
            PolymorphicJsonAdapterFactory.of(BaseType::class.java, "type")
              .withSubtype(BaseType.TypeA::class.java, "a")
              .withSubtype(BaseType.TypeA::class.java, "aa")
              .withSubtype(BaseType.TypeB::class.java, "b")
              .create(BaseType::class.java, emptySet(), moshi) as JsonAdapter<BaseType>
      
      
        public override fun fromJson(reader: JsonReader): BaseType? = runtimeAdapter.fromJson(reader)
      
        public override fun toJson(writer: JsonWriter, value: BaseType?): Unit {
          runtimeAdapter.toJson(writer, value)
        }
      }
    """.trimIndent())

    val proguardFiles = result.generatedFiles.filter { it.extension == "pro" }
    check(proguardFiles.isNotEmpty())
    proguardFiles.filter { it.extension == "pro" }.forEach { file ->
      when (file.nameWithoutExtension) {
        "moshi-sealed-test.BaseType" -> assertThat(file.readText()).contains(
          """
          -if class test.BaseType
          -keepnames class test.BaseType
          -if class test.BaseType
          -keep class test.BaseTypeJsonAdapter {
              public <init>(com.squareup.moshi.Moshi);
          }
          """.trimIndent()
        )
        else -> error("Unrecognized proguard file: $file")
      }
    }
  }

}