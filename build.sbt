import sbt._
import Process._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.debian.Keys._
import com.typesafe.sbt.packager.linux.LinuxPackageMapping
import S3._

seq(packagerSettings:_*)

maintainer in Debian:= "Rajthilak <rajthilak@megam.co.in>"

packageSummary := "Unique ID Generator - Twitters Snowflake."

packageDescription in Debian:= "A Unique ID Generator -Twitter's Snowflake project. Leveraged by megam platform to spit out unique 64 bit IDs."

com.typesafe.sbt.packager.debian.Keys.name in Debian := "megamsnowflake"

scalaVersion := "2.10.3"

s3Settings

S3.progress in S3.upload := true

resolvers += "Typesafe Snapshots" at "http://repo.typesafe.com/typesafe/snapshots"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

resolvers += "Scala-Tools Maven2 Snapshots Repository" at "http://scala-tools.org/repo-snapshots"

resolvers += "Twitter Repo" at "http://maven.twttr.com"

TaskKey[Unit]("sunburn") := { 
	println("Unzipping ...snowflake-package-dist.zip into target/debpkg") 
	IO.unzip(new java.io.File("target/snowflake-package-dist.zip"), new java.io.File("target/debpkg"))	
}

linuxPackageMappings <+= (baseDirectory in Debian) map { bd =>
  packageMapping(
    (bd / "LICENSE") -> "/usr/local/share/megamsnowflake/LICENSE"
  ) withPerms "0644" asDocs()
}


linuxPackageMappings in Debian <+= (baseDirectory) map { bd =>
  (packageMapping((bd / "target" / "debpkg" / "scripts" / "snowflake") -> "/usr/local/share/megamsnowflake/bin/start")
   withUser "root" withGroup "root" withPerms "0755")
}

linuxPackageMappings in Debian <+= (baseDirectory) map { bd =>
  (packageMapping((bd / "target" / "debpkg" / "scripts" / "client_test.rb") -> "/usr/local/share/megamsnowflake/bin/client.rb")
   withUser "root" withGroup "root" withPerms "0755")
}


linuxPackageMappings <+= (baseDirectory) map { bd =>
  val src = bd / "target/debpkg/libs"
  val dest = "/usr/local/share/megamsnowflake/libs"
  LinuxPackageMapping(
    for {
      path <- (src ***).get
      if !path.isDirectory
    } yield path -> path.toString.replaceFirst(src.toString, dest)
  )
}


linuxPackageMappings in Debian <+= (baseDirectory) map { bd =>
  (packageMapping((bd / "target/debpkg/config/development.scala") -> "/usr/local/share/megamsnowflake/config/development.scala")
   withConfig())
}

 
com.typesafe.sbt.packager.debian.Keys.version in Debian <<= (com.typesafe.sbt.packager.debian.Keys.version, sbtVersion) apply { (v, sv) =>
  sv + "-build-" + (v split "\\." map (_.toInt) dropWhile (_ == 0) map ("%02d" format _) mkString "")
}

debianPackageDependencies in Debian ++= Seq("curl", "java2-runtime", "bash (>= 2.05a-11)")

debianPackageRecommends in Debian += "zookeeper"

linuxPackageMappings in Debian <+= (com.typesafe.sbt.packager.debian.Keys.sourceDirectory) map { bd =>
  (packageMapping(
    (bd / "CHANGELOG") -> "/usr/local/share/megamsnowflake/changelog.gz"
  ) withUser "root" withGroup "root" withPerms "0644" gzipped) asDocs()
}


mappings in upload := Seq((new java.io.File(("%s.jar") format("target/snowflake")),"jars/snowflake.jar"),
					(new java.io.File(("%s-%s.deb") format("target/megamsnowflake", "0.12.3-build-0100")),"debs/megamsnowflake-0.1.0.deb"))

host in upload := "megampub.s3.amazonaws.com"

credentials += Credentials(Path.userHome / "software" / "aws" / "keys" / "sbt_s3_keys")