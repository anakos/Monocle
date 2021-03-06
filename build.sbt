import com.typesafe.sbt.pgp.PgpKeys.publishSigned
import com.typesafe.sbt.SbtSite.SiteKeys._
import com.typesafe.sbt.SbtGhPages.GhPagesKeys._
import com.typesafe.tools.mima.plugin.MimaKeys.previousArtifact
import pl.project13.scala.sbt.SbtJmh._
import sbt.Keys._
import sbtrelease.ReleaseStateTransformations._
import ReleaseTransformations._
import sbtunidoc.Plugin.UnidocKeys._

lazy val buildSettings = Seq(
  organization       := "com.github.julien-truffaut",
  scalaVersion       := "2.11.7",
  crossScalaVersions := Seq("2.10.5", "2.11.7"),
  scalacOptions     ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:implicitConversions", "-language:higherKinds", "-language:postfixOps",
    "-unchecked",
    "-Xfatal-warnings",
    "-Yinline-warnings",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-value-discard",
    "-Xfuture"
  ),
  resolvers ++= Seq(
    "bintray/non" at "http://dl.bintray.com/non/maven",
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  ),
  scmInfo := Some(ScmInfo(url("https://github.com/julien-truffaut/Monocle"), "scm:git:git@github.com:julien-truffaut/Monocle.git"))
)

lazy val scalaz     = "org.scalaz"      %% "scalaz-core" % "7.1.3"
lazy val shapeless  = "com.chuusai"     %% "shapeless"   % "2.2.5"

lazy val discpline  = "org.typelevel"   %% "discipline"  % "0.3"
lazy val scalatest  = "org.scalatest"   %% "scalatest"   % "2.2.4"  % "test"

lazy val macroVersion = "2.0.1"
lazy val paradisePlugin = compilerPlugin("org.scalamacros" %  "paradise"       % macroVersion cross CrossVersion.full)
lazy val kindProjector  = compilerPlugin("org.spire-math"  %% "kind-projector" % "0.6.0")

def mimaSettings(module: String): Seq[Setting[_]] = mimaDefaultSettings ++ Seq(
  previousArtifact := Some("com.github.julien-truffaut" %  (s"monocle-${module}_2.11") % "1.1.0")
)

lazy val monocleSettings = buildSettings ++ publishSettings

lazy val monocle = project.in(file("."))
  .settings(moduleName := "monocle")
  .settings(monocleSettings)
  .aggregate(core, generic, law, macros, state, test, example, docs, bench)
  .dependsOn(core, generic, law, macros, state, test % "test-internal -> test", bench % "compile-internal;test-internal -> test")

lazy val core = project
  .settings(moduleName := "monocle-core")
  .settings(monocleSettings)
  .settings(mimaSettings("core"))
  .settings(libraryDependencies := Seq(scalaz, compilerPlugin(kindProjector)))

lazy val generic = project.dependsOn(core)
  .settings(moduleName := "monocle-generic")
  .settings(monocleSettings)
  .settings(mimaSettings("generic"))
  .settings(libraryDependencies := Seq(scalaz, shapeless))

lazy val law = project.dependsOn(core)
  .settings(moduleName := "monocle-law")
  .settings(monocleSettings)
  .settings(libraryDependencies := Seq(discpline))

lazy val macros = project.dependsOn(core)
  .in(file("macro"))
  .settings(moduleName := "monocle-macro")
  .settings(monocleSettings)
  .settings(Seq(
  scalacOptions  += "-language:experimental.macros",
  libraryDependencies ++= Seq(
    "org.scala-lang"  %  "scala-reflect"  % scalaVersion.value,
    "org.scala-lang"  %  "scala-compiler" % scalaVersion.value % "provided"
  ),
  addCompilerPlugin(paradisePlugin),
  libraryDependencies ++= CrossVersion partialVersion scalaVersion.value collect {
    case (2, scalaMajor) if scalaMajor < 11 => Seq("org.scalamacros" %% "quasiquotes" % macroVersion)
  } getOrElse Nil,
  unmanagedSourceDirectories in Compile += (sourceDirectory in Compile).value / s"scala-${scalaBinaryVersion.value}"
  ))

lazy val state = project.dependsOn(core)
  .settings(moduleName := "monocle-state")
  .settings(monocleSettings)
  .settings(libraryDependencies := Seq(scalaz))

lazy val test = project.dependsOn(core, generic, macros, law, state)
  .settings(moduleName := "monocle-test")
  .settings(monocleSettings)
  .settings(noPublishSettings)
  .settings(
    libraryDependencies ++= Seq(scalaz, shapeless, scalatest, compilerPlugin(paradisePlugin))
  )

lazy val bench = project
  .settings(moduleName := "monocle-bench")
  .settings(monocleSettings)
  .settings(noPublishSettings)
  .settings(jmhSettings)
  .settings(libraryDependencies ++= Seq(
    "com.github.julien-truffaut" %%  "monocle-core"  % "1.2.0-SNAPSHOT",
    "com.github.julien-truffaut" %%  "monocle-macro" % "1.2.0-SNAPSHOT",
    shapeless,
    compilerPlugin(kindProjector),
    compilerPlugin(paradisePlugin)
  ))

lazy val example = project.dependsOn(core, generic, macros, state, test % "test->test")
  .settings(moduleName := "monocle-example")
  .settings(monocleSettings)
  .settings(noPublishSettings)
  .settings(
    libraryDependencies ++= Seq(scalaz, shapeless, scalatest, compilerPlugin(paradisePlugin))
  )

lazy val docs = project.dependsOn(core, example)
  .settings(moduleName := "monocle-docs")
  .settings(monocleSettings)
  .settings(noPublishSettings)
  .settings(unidocSettings)
  .settings(site.settings)
  .settings(ghpages.settings)
  .settings(docSettings)
  .settings(tutSettings)
  .settings(
    libraryDependencies ++= Seq(scalaz, shapeless, compilerPlugin(kindProjector), compilerPlugin(paradisePlugin))
  )


lazy val docSettings = Seq(
  autoAPIMappings := true,
  unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(core),
  site.addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), "api"),
  site.addMappingsToSiteDir(tut, "_tut"),
  ghpagesNoJekyll := false,
  scalacOptions in (ScalaUnidoc, unidoc) ++= Seq(
    "-doc-source-url", scmInfo.value.get.browseUrl + "/tree/master€{FILE_PATH}.scala",
    "-sourcepath", baseDirectory.in(LocalRootProject).value.getAbsolutePath
  ),
  git.remoteRepo := "git@github.com:julien-truffaut/Monocle.git",
  includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.swf" | "*.yml" | "*.md"
)


lazy val publishSettings = Seq(
  homepage := Some(url("https://github.com/julien-truffaut/Monocle")),
  licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT")),
  autoAPIMappings := true,
  apiURL := Some(url("https://julien-truffaut.github.io/Monocle/api/")),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  publishTo <<= version { (v: String) =>
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  pomExtra := (
    <developers>
      <developer>
        <id>julien-truffaut</id>
        <name>Julien Truffaut</name>
      </developer>
      <developer>
        <id>NightRa</id>
        <name>Ilan Godik</name>
      </developer>
    </developers>
    ),
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    setNextVersion,
    commitNextVersion,
    pushChanges
  )
)

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

addCommandAlias("validate", ";compile;test;unidoc;tut")

// For Travis CI - see http://www.cakesolutions.net/teamblogs/publishing-artefacts-to-oss-sonatype-nexus-using-sbt-and-travis-ci
credentials ++= (for {
  username <- Option(System.getenv().get("SONATYPE_USERNAME"))
  password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
} yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq