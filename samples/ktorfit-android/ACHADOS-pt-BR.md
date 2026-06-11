# ktorfit × kmp-targets — Experimento Android-only (pt-BR)

> Documento de achados do experimento que testou a hipótese: **"o `kmp-targets` não
> funciona com o ktorfit quando um módulo seleciona apenas Android (sem iOS)"**.

---

## 1. Pergunta e contexto

O [ktorfit](https://foso.github.io/Ktorfit/) é uma biblioteca de cliente HTTP no estilo Retrofit
para Kotlin Multiplatform (KMP). Ele gera o código de implementação das suas interfaces de API via
**KSP** (Kotlin Symbol Processing) — por exemplo, a extensão `Ktorfit.createMinhaApi()`.

O `kmp-targets` registra os alvos (targets) do KMP **dinamicamente**, conforme a seleção
(`kmpTargets { supports { … } } ∩ kmptargets.targets`). A dúvida era: quando o módulo registra
**apenas o alvo Android**, o ktorfit continua funcionando?

## 2. Resposta curta

**Sim — funciona, com uma ressalva sobre *onde* o código do ktorfit fica.**

O processador KSP do ktorfit roda corretamente para uma seleção Android-only e gera o
`createFakeApi()`. A única pega é o formato de um módulo KMP de **alvo único**: ele **não** possui
uma compilação de metadados de `commonMain`, então a interface + o ponto de chamada do ktorfit
precisam morar no source set do próprio alvo (**`androidMain`**), e **não** em `commonMain`. Com o
código em `androidMain`, o build Android-only **passa**.

> Isso **não é um bug do `kmp-targets`** — o plugin registra fielmente exatamente o único alvo
> solicitado. A restrição é inteiramente sobre como o ktorfit/KSP lida com um grafo KMP de alvo único.

## 3. O que o experimento contém

Build standalone (consome o plugin do `mavenLocal()`, igual ao `samples/hello-world`) com a **mesma**
API falsa do ktorfit em dois módulos:

| Módulo | `kmpTargets { supports { … } }` | Local do código | Resultado |
| --- | --- | --- | --- |
| `:api-android-only` | `androidTarget` | `androidMain` | **BUILD SUCCESSFUL** |
| `:api-multiplatform` (controle) | `androidTarget + jvm` | `commonMain` | **BUILD SUCCESSFUL** |

A "API" é propositalmente falsa (`FakeApi.ping()`); o único objetivo dela é **forçar** o processador
KSP do ktorfit a gerar a extensão `createFakeApi()` e provar que o ponto de chamada consegue
resolvê-la.

## 4. O que funciona e o que não funciona

O ktorfit gera a extensão `create<Api>()` via KSP. *Onde* esse símbolo gerado cai — e qual
compilação consegue enxergá-lo — é o cerne de tudo:

- ✅ **Android-only, API em `androidMain`:** a task `kspDebugKotlinAndroid` emite `createFakeApi` em
  `build/generated/ksp/android/androidDebug/…`, diretório que o plugin do KSP **já** conecta à
  compilação Android. Interface + extensão gerada compilam juntas. **Funciona.**
- ✅ **Android + JVM (controle), API em `commonMain`:** dois alvos ⇒ o KGP cria uma compilação de
  metadados de `commonMain` ⇒ a task `kspCommonMainKotlinMetadata` roda e emite em
  `build/generated/ksp/metadata/commonMain/kotlin/…`, que está no source path do `commonMain`.
  **Funciona.**
- ❌ **Android-only, API em `commonMain`:** um alvo único **não** tem compilação de metadados de
  `commonMain`, logo **não existe** a task `kspCommonMainKotlinMetadata` e nada coloca o
  `createFakeApi` no path do `commonMain`. A task `compileKotlinMetadata` falha com
  `Unresolved reference 'createFakeApi'`.
- ❌ **O "workaround da task de metadados" para o caso de alvo único.** A correção mais citada —
  `dependsOn("kspCommonMainKotlinMetadata")` + adicionar o diretório KSP de metadados ao `commonMain`
  — **não funciona** para um alvo realmente único, porque essa task não existe. Falha já na
  configuração com `Task with name 'kspCommonMainKotlinMetadata' not found` (exatamente a
  [issue #593](https://github.com/Foso/Ktorfit/issues/593)).

Tudo isso bate com as issues conhecidas do ktorfit para cenários KMP estreitos/Android-only:
[#593](https://github.com/Foso/Ktorfit/issues/593) (`kspCommonMainKotlinMetadata` não encontrada),
[#965](https://github.com/Foso/Ktorfit/issues/965) (código gerado ausente com apenas o alvo Android)
e [#638](https://github.com/Foso/Ktorfit/issues/638) (plugin Gradle do ktorfit × plugin de library
KMP do Android).

## 5. Causa-raiz (em detalhe)

Em um build KMP, o código `commonMain` é produzido pela task **`kspCommonMainKotlinMetadata`** e
emitido em `build/generated/ksp/metadata/commonMain/kotlin/…`, que é uma raiz de fontes do
`commonMain`.

- **Controle (Android + JVM):** existem `compileCommonMainKotlinMetadata` /
  `kspCommonMainKotlinMetadata` → `createFakeApi` cai em `…/ksp/metadata/commonMain/…` → o
  `commonMain` resolve. ✅
- **Android-only:** com um **único alvo**, o KGP **não** cria a compilação de metadados de
  `commonMain`, então `kspCommonMainKotlinMetadata` nunca existe. O KSP do ktorfit só emite o
  `createFakeApi` no diretório por-alvo `build/generated/ksp/android/androidDebug/…`, que **não** está
  no path do `commonMain` → o ponto de chamada em `commonMain` não enxerga → compilação falha. ❌

A correção é mover o ponto de chamada para `androidMain`: aí ele participa da **mesma** compilação
Android que já recebe o diretório gerado pelo KSP.

## 6. Como fazer ktorfit + KSP funcionar com Android-only

Duas opções limpas — ambas do lado ktorfit/KSP, **nenhuma** é responsabilidade do `kmp-targets`:

### Opção A (recomendada para alvo único) — manter a API em `androidMain`

Em um módulo de alvo único não há código realmente "comum", então colocar a interface + a chamada
`create<Api>()` no source set do alvo não custa nada e resolve o problema.

```
api-android-only/
└── src/
    └── androidMain/kotlin/com/rsicarelli/ktorfit/sample/FakeApi.kt   ← aqui, NÃO em commonMain
```

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.kmpTargets)
}

android {
    namespace = "com.rsicarelli.ktorfit.sample.androidonly"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}

ktorfit {
    // Kotlin 2.3.x exige fixar a versão do compiler plugin do ktorfit explicitamente
    // (a antiga `kotlinVersion` está deprecada). Veja a tabela de compatibilidade do ktorfit.
    compilerPluginVersion.set(libs.versions.ktorfitCompilerPlugin.get()) // "2.3.3"
}

kotlin {
    sourceSets {
        // A dependência do ktorfit fica em commonMain (androidMain herda). O CÓDIGO fica em androidMain.
        commonMain.dependencies { implementation(libs.ktorfit.lib) }
    }
}

// O ponto central: SOMENTE ANDROID. Sem iOS, sem JVM.
kmpTargets { supports { androidTarget } }
```

```kotlin
// src/androidMain/kotlin/.../FakeApi.kt
package com.rsicarelli.ktorfit.sample

import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.http.GET

interface FakeApi {
    @GET("ping") suspend fun ping(): String
}

// Chama a extensão GERADA pelo ktorfit — sua resolução é o teste inteiro.
fun fakeApi(): FakeApi =
    Ktorfit.Builder().baseUrl("https://example.com/").build().createFakeApi()
```

### Opção B — dar um segundo alvo ao build

Adicione um segundo alvo (ex.: `jvm`) para que exista uma compilação de metadados de `commonMain` —
e, portanto, a task `kspCommonMainKotlinMetadata` — mantendo a API em `commonMain`. É exatamente o
módulo de controle (`:api-multiplatform`).

```kotlin
kmpTargets { supports { androidTarget + jvm } }
```

### ⚠️ O que NÃO fazer em alvo único

Não tente o wiring manual de `kspCommonMainKotlinMetadata`:

```kotlin
// NÃO funciona com um único alvo — a task referenciada nunca é criada (Foso/Ktorfit#593):
dependencies { add("kspCommonMainMetadata", "de.jensklingenberg.ktorfit:ktorfit-ksp:2.7.1") }
tasks.withType<KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata") // ← explode aqui
}
```

Erro resultante:

```
> Task with name 'kspCommonMainKotlinMetadata' not found in project ':api-android-only'.
```

## 7. Como o KSP se comporta em KMP de alvo único (resumo mental)

- O processador KSP do ktorfit **roda** por variante do alvo Android: `kspDebugKotlinAndroid`,
  `kspReleaseKotlinAndroid`. Você pode confirmar em
  `./gradlew :api-android-only:tasks --all | grep -i ksp`.
- O código gerado (`_FakeApiImpl.kt`, contendo `fun Ktorfit.createFakeApi()`) sai em
  `build/generated/ksp/android/androidDebug/kotlin/…`.
- O plugin do KSP já adiciona esse diretório às fontes da **compilação Android**. Logo, qualquer
  código que precise enxergar `createFakeApi()` deve estar na **mesma compilação** — ou seja, em
  `androidMain` (ou `androidDebug`/`androidRelease`), não em `commonMain`.
- Não existe `kspCommonMainKotlinMetadata` quando há um único alvo, porque o KGP não cria a
  compilação de metadados comum. Essa é a raiz de toda a confusão.

## 8. Duas ressalvas de AGP 9 encontradas no caminho

1. **`com.android.library` + KMP é rejeitado no AGP 9.0+.** O `kmp-targets` registra o Android via o
   clássico `androidTarget()` do KGP, que precisa do plugin de library clássico. Para manter esse
   caminho no AGP 9, este sample define `android.builtInKotlin=false` e `android.newDsl=false` (veja
   `gradle.properties`). A alternativa moderna — `com.android.kotlin.multiplatform.library` com
   `kotlin { androidLibrary { … } }` — **não** é o que o `androidTarget()` do `kmp-targets` dirige.
2. O **config cache** ficou **desligado** aqui para manter o sinal de AGP + KSP + ktorfit limpo.
3. O **metaspace do daemon** precisou de mais folga (AGP + KSP + KMP em um build só estouravam o
   padrão e o daemon expirava no meio). Definido `org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=1g`.

## 9. Versões usadas

| Ferramenta | Versão |
| --- | --- |
| Kotlin | 2.3.21 |
| AGP | 9.2.1 |
| KSP | 2.3.9 |
| ktorfit (lib + plugin) | 2.7.1 |
| ktorfit `compilerPluginVersion` | 2.3.3 (obrigatório para Kotlin 2.3.x) |
| Gradle | 9.5.1 (wrapper do repo) |
| kmp-targets | 0.1.0-SNAPSHOT (mavenLocal) |

O ktorfit 2.7.1 precisa do compiler plugin fixado para Kotlin 2.3.x — note o bloco
`ktorfit { compilerPluginVersion.set("2.3.3") }` no `build.gradle.kts` de cada módulo.

## 10. Como reproduzir

```bash
# 1. Publicar o plugin sob teste no ~/.m2
task publish-local           # ou: ./gradlew publishToMavenLocal

# 2. Apontar para um Android SDK (compileSdk 36) — o local.properties deste sample guarda o sdk.dir
export ANDROID_HOME=/caminho/para/android-sdk

# 3. Android-only → PASSA (API mora em androidMain)
./gradlew -p samples/ktorfit-android :api-android-only:build

# 4. Controle, Android + JVM → PASSA (API mora em commonMain)
./gradlew -p samples/ktorfit-android :api-multiplatform:build
```

Para ver a falha você mesmo: mova
`api-android-only/src/androidMain/kotlin/.../FakeApi.kt` de volta para `commonMain` e rebuilde — a
task `compileKotlinMetadata` falha com `Unresolved reference 'createFakeApi'`.

> O `local.properties` (o `sdk.dir`) está no `.gitignore` — defina `ANDROID_HOME` ou crie-o
> localmente.

## 11. Veredito final

A hipótese "o `kmp-targets` não funciona com o ktorfit" **se confirma apenas parcialmente** e por um
motivo que **não é** do `kmp-targets`:

- O `kmp-targets` faz exatamente o que promete: registra só o `androidTarget`.
- O ktorfit/KSP é que **não** monta uma compilação de metadados comum para um alvo único — então a
  API do ktorfit não pode viver em `commonMain` nesse cenário.
- **Solução**: coloque a API em `androidMain` (alvo único) **ou** adicione um segundo alvo. Ambas são
  decisões de configuração ktorfit/KSP, e o build Android-only passa.
