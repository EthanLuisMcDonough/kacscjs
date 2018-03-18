name := """kacscjs"""
organization := "com.ethanmcdonough"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.12.4"

libraryDependencies += guice
libraryDependencies += "com.github.scribejava" % "scribejava-core" % "5.2.0-java7again"
libraryDependencies += "mysql" % "mysql-connector-java" % "8.0.9-rc"
libraryDependencies += "commons-codec" % "commons-codec" % "1.11"
libraryDependencies += "org.apache.httpcomponents" % "httpclient" % "4.5.5"
libraryDependencies += "org.webjars" % "material-design-lite" % "1.3.0"
libraryDependencies += "org.webjars" % "material-design-icons" % "3.0.1"