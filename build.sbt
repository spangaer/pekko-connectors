lazy val pekkoConnectors = project
  .in(file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .disablePlugins(MimaPlugin, SitePlugin)
  .aggregate(
    amqp,
    avroparquet,
    awslambda,
    azureStorageQueue,
    cassandra,
    couchbase,
    csv,
    dynamodb,
    elasticsearch,
    eventbridge,
    files,
    ftp,
    geode,
    googleCommon,
    googleCloudBigQuery,
    googleCloudBigQueryStorage,
    googleCloudPubSub,
    googleCloudPubSubGrpc,
    googleCloudStorage,
    googleFcm,
    hbase,
    hdfs,
    huaweiPushKit,
    influxdb,
    ironmq,
    jms,
    jsonStreaming,
    kinesis,
    kudu,
    mongodb,
    mqtt,
    mqttStreaming,
    orientdb,
    pravega,
    reference,
    s3,
    springWeb,
    simpleCodecs,
    slick,
    sns,
    solr,
    sqs,
    sse,
    text,
    udp,
    unixdomainsocket,
    xml
  )
  .aggregate(`doc-examples`)
  .settings(
    onLoadMessage :=
      """
        |** Welcome to the sbt build definition for Pekko Connectors! **
        |
        |Useful sbt tasks:
        |
        |  docs/previewSite - builds Paradox and Scaladoc documentation,
        |    starts a webserver and opens a new browser window
        |
        |  test - runs all the tests for all of the connectors.
        |    Make sure to run `docker-compose up` first.
        |
        |  mqtt/testOnly *.MqttSourceSpec - runs a single test
        |
        |  mimaReportBinaryIssues - checks whether this current API
        |    is binary compatible with the released version
      """.stripMargin,
    // unidoc combines sources and jars from all connectors and that
    // might include some incompatible ones. Depending on the
    // classpath order that might lead to scaladoc compilation errors.
    // Therefore some versions are excluded here.
    ScalaUnidoc / unidoc / fullClasspath := {
      (ScalaUnidoc / unidoc / fullClasspath).value
        .filterNot(_.data.getAbsolutePath.contains("protobuf-java-2.5.0.jar"))
        .filterNot(_.data.getAbsolutePath.contains("guava-28.1-android.jar"))
        .filterNot(_.data.getAbsolutePath.contains("commons-net-3.1.jar"))
        .filterNot(_.data.getAbsolutePath.contains("protobuf-java-2.6.1.jar"))
    },
    ScalaUnidoc / unidoc / unidocProjectFilter := inAnyProject
      -- inProjects(
        `doc-examples`,
        csvBench,
        mqttStreamingBench,
        // googleCloudPubSubGrpc and googleCloudBigQueryStorage contain the same gRPC generated classes
        // don't include ScalaDocs for googleCloudBigQueryStorage to make it work
        googleCloudBigQueryStorage,
        // springWeb triggers an esoteric ScalaDoc bug (from Java code)
        springWeb
      ),
    licenses := List(License.Apache2),
    crossScalaVersions := List() // workaround for https://github.com/sbt/sbt/issues/3465
  )

TaskKey[Unit]("verifyCodeFmt") := {
  javafmtCheckAll.all(ScopeFilter(inAnyProject)).result.value.toEither.left.foreach { _ =>
    throw new MessageOnlyException(
      "Unformatted Java code found. Please run 'javafmtAll' and commit the reformatted code"
    )
  }
  scalafmtCheckAll.all(ScopeFilter(inAnyProject)).result.value.toEither.left.foreach { _ =>
    throw new MessageOnlyException(
      "Unformatted Scala code found. Please run 'scalafmtAll' and commit the reformatted code"
    )
  }
  (Compile / scalafmtSbtCheck).result.value.toEither.left.foreach { _ =>
    throw new MessageOnlyException(
      "Unformatted sbt code found. Please run 'scalafmtSbt' and commit the reformatted code"
    )
  }
}

addCommandAlias("verifyCodeStyle", "headerCheck; verifyCodeFmt")

lazy val amqp = pekkoConnectorProject("amqp", "amqp", Dependencies.Amqp)

lazy val avroparquet =
  pekkoConnectorProject("avroparquet", "avroparquet", Dependencies.AvroParquet)

lazy val awslambda = pekkoConnectorProject("awslambda", "aws.lambda", Dependencies.AwsLambda)

lazy val azureStorageQueue = pekkoConnectorProject(
  "azure-storage-queue",
  "azure.storagequeue",
  Dependencies.AzureStorageQueue
)

lazy val cassandra =
  pekkoConnectorProject("cassandra", "cassandra", Dependencies.Cassandra)

lazy val couchbase =
  pekkoConnectorProject("couchbase", "couchbase", Dependencies.Couchbase)

lazy val csv = pekkoConnectorProject("csv", "csv")

lazy val csvBench = internalProject("csv-bench")
  .dependsOn(csv)
  .enablePlugins(JmhPlugin)

lazy val dynamodb = pekkoConnectorProject("dynamodb", "aws.dynamodb", Dependencies.DynamoDB)

lazy val elasticsearch = pekkoConnectorProject(
  "elasticsearch",
  "elasticsearch",
  Dependencies.Elasticsearch
)

// The name 'file' is taken by `sbt.file`, hence 'files'
lazy val files = pekkoConnectorProject("file", "file", Dependencies.File)

lazy val ftp = pekkoConnectorProject(
  "ftp",
  "ftp",
  Dependencies.Ftp,
  Test / fork := true,
  // To avoid potential blocking in machines with low entropy (default is `/dev/random`)
  Test / javaOptions += "-Djava.security.egd=file:/dev/./urandom"
)

lazy val geode =
  pekkoConnectorProject(
    "geode",
    "geode",
    Dependencies.Geode,
    Test / fork := true,
    // https://github.com/scala/bug/issues/12072
    Test / scalacOptions += "-Xlint:-byname-implicit"
  )

lazy val googleCommon = pekkoConnectorProject(
  "google-common",
  "google.common",
  Dependencies.GoogleCommon,
  Test / fork := true
)

lazy val googleCloudBigQuery = pekkoConnectorProject(
  "google-cloud-bigquery",
  "google.cloud.bigquery",
  Dependencies.GoogleBigQuery,
  Test / fork := true,
  Compile / scalacOptions += "-Wconf:src=src_managed/.+:s"
).dependsOn(googleCommon).enablePlugins(spray.boilerplate.BoilerplatePlugin)

lazy val googleCloudBigQueryStorage = pekkoConnectorProject(
  "google-cloud-bigquery-storage",
  "google.cloud.bigquery.storage",
  Dependencies.GoogleBigQueryStorage,
  akkaGrpcCodeGeneratorSettings ~= { _.filterNot(_ == "flat_package") },
  akkaGrpcCodeGeneratorSettings += "server_power_apis",
  // FIXME only generate the server for the tests again
  akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client, AkkaGrpc.Server),
  // Test / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server),
  akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala, AkkaGrpc.Java),
  Compile / scalacOptions ++= Seq(
      "-Wconf:src=.+/akka-grpc/main/.+:s",
      "-Wconf:src=.+/akka-grpc/test/.+:s"
    ),
  compile / javacOptions := (compile / javacOptions).value.filterNot(_ == "-Xlint:deprecation")
).dependsOn(googleCommon).enablePlugins(AkkaGrpcPlugin)

lazy val googleCloudPubSub = pekkoConnectorProject(
  "google-cloud-pub-sub",
  "google.cloud.pubsub",
  Dependencies.GooglePubSub,
  Test / fork := true,
  // See docker-compose.yml gcloud-pubsub-emulator_prep
  Test / envVars := Map("PUBSUB_EMULATOR_HOST" -> "localhost", "PUBSUB_EMULATOR_PORT" -> "8538")
).dependsOn(googleCommon)

lazy val googleCloudPubSubGrpc = pekkoConnectorProject(
  "google-cloud-pub-sub-grpc",
  "google.cloud.pubsub.grpc",
  Dependencies.GooglePubSubGrpc,
  akkaGrpcCodeGeneratorSettings ~= { _.filterNot(_ == "flat_package") },
  akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client),
  akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala, AkkaGrpc.Java),
  // for the ExampleApp in the tests
  run / connectInput := true,
  Compile / scalacOptions ++= Seq(
      "-Wconf:src=.+/akka-grpc/main/.+:s",
      "-Wconf:src=.+/akka-grpc/test/.+:s"
    ),
  compile / javacOptions := (compile / javacOptions).value.filterNot(_ == "-Xlint:deprecation")
).enablePlugins(AkkaGrpcPlugin).dependsOn(googleCommon)

lazy val googleCloudStorage = pekkoConnectorProject(
  "google-cloud-storage",
  "google.cloud.storage",
  Test / fork := true,
  Dependencies.GoogleStorage
).dependsOn(googleCommon)

lazy val googleFcm =
  pekkoConnectorProject("google-fcm", "google.firebase.fcm", Dependencies.GoogleFcm, Test / fork := true)
    .dependsOn(googleCommon)

lazy val hbase = pekkoConnectorProject("hbase", "hbase", Dependencies.HBase, Test / fork := true)

lazy val hdfs = pekkoConnectorProject("hdfs", "hdfs", Dependencies.Hdfs)

lazy val huaweiPushKit =
  pekkoConnectorProject("huawei-push-kit", "huawei.pushkit", Dependencies.HuaweiPushKit)

lazy val influxdb = pekkoConnectorProject(
  "influxdb",
  "influxdb",
  Dependencies.InfluxDB,
  Compile / scalacOptions ++= Seq(
      // JDK 11: method isAccessible in class AccessibleObject is deprecated
      "-Wconf:cat=deprecation:s"
    )
)

lazy val ironmq = pekkoConnectorProject(
  "ironmq",
  "ironmq",
  Dependencies.IronMq,
  Test / fork := true
)

lazy val jms = pekkoConnectorProject("jms", "jms", Dependencies.Jms)

lazy val jsonStreaming = pekkoConnectorProject("json-streaming", "json.streaming", Dependencies.JsonStreaming)

lazy val kinesis = pekkoConnectorProject("kinesis", "aws.kinesis", Dependencies.Kinesis)

lazy val kudu = pekkoConnectorProject("kudu", "kudu", Dependencies.Kudu)

lazy val mongodb = pekkoConnectorProject("mongodb", "mongodb", Dependencies.MongoDb)

lazy val mqtt = pekkoConnectorProject("mqtt", "mqtt", Dependencies.Mqtt)

lazy val mqttStreaming =
  pekkoConnectorProject("mqtt-streaming", "mqttStreaming", Dependencies.MqttStreaming)
lazy val mqttStreamingBench = internalProject("mqtt-streaming-bench")
  .enablePlugins(JmhPlugin)
  .dependsOn(mqtt, mqttStreaming)

lazy val orientdb =
  pekkoConnectorProject(
    "orientdb",
    "orientdb",
    Dependencies.OrientDB,
    Test / fork := true,
    // note: orientdb client needs to be refactored to move off deprecated calls
    fatalWarnings := false
  )

lazy val reference = internalProject("reference", Dependencies.Reference)
  .dependsOn(testkit % Test)

lazy val s3 = pekkoConnectorProject("s3", "aws.s3", Dependencies.S3)

lazy val pravega = pekkoConnectorProject(
  "pravega",
  "pravega",
  Dependencies.Pravega,
  Test / fork := true
)

lazy val springWeb = pekkoConnectorProject(
  "spring-web",
  "spring.web",
  Dependencies.SpringWeb
)

lazy val simpleCodecs = pekkoConnectorProject("simple-codecs", "simplecodecs")

lazy val slick = pekkoConnectorProject("slick", "slick", Dependencies.Slick)

lazy val eventbridge =
  pekkoConnectorProject("aws-event-bridge", "aws.eventbridge", Dependencies.Eventbridge)

lazy val sns = pekkoConnectorProject("sns", "aws.sns", Dependencies.Sns)

lazy val solr = pekkoConnectorProject("solr", "solr", Dependencies.Solr)

lazy val sqs = pekkoConnectorProject("sqs", "aws.sqs", Dependencies.Sqs)

lazy val sse = pekkoConnectorProject("sse", "sse", Dependencies.Sse)

lazy val text = pekkoConnectorProject("text", "text")

lazy val udp = pekkoConnectorProject("udp", "udp")

lazy val unixdomainsocket =
  pekkoConnectorProject("unix-domain-socket", "unixdomainsocket", Dependencies.UnixDomainSocket)

lazy val xml = pekkoConnectorProject("xml", "xml", Dependencies.Xml)

lazy val docs = project
  .enablePlugins(AkkaParadoxPlugin, ParadoxSitePlugin, PreprocessPlugin, PublishRsyncPlugin)
  .disablePlugins(MimaPlugin)
  .settings(
    Compile / paradox / name := "Alpakka",
    publish / skip := true,
    makeSite := makeSite.dependsOn(LocalRootProject / ScalaUnidoc / doc).value,
    previewPath := (Paradox / siteSubdirName).value,
    Preprocess / siteSubdirName := s"api/alpakka/${projectInfoVersion.value}",
    Preprocess / sourceDirectory := (LocalRootProject / ScalaUnidoc / unidoc / target).value,
    Preprocess / preprocessRules := Seq(
        ("http://www\\.eclipse\\.org/".r, _ => "https://www\\.eclipse\\.org/"),
        ("http://pravega\\.io/".r, _ => "https://pravega\\.io/"),
        ("http://www\\.scala-lang\\.org/".r, _ => "https://www\\.scala-lang\\.org/"),
        ("https://javadoc\\.io/page/".r, _ => "https://javadoc\\.io/static/")
      ),
    Paradox / siteSubdirName := s"docs/alpakka/${projectInfoVersion.value}",
    paradoxProperties ++= Map(
        "akka.version" -> Dependencies.AkkaVersion,
        "akka-http.version" -> Dependencies.AkkaHttpVersion,
        "hadoop.version" -> Dependencies.HadoopVersion,
        "extref.github.base_url" -> s"https://github.com/akka/alpakka/tree/${if (isSnapshot.value) "master"
        else "v" + version.value}/%s",
        "extref.akka.base_url" -> s"https://doc.akka.io/docs/akka/${Dependencies.AkkaBinaryVersion}/%s",
        "scaladoc.akka.base_url" -> s"https://doc.akka.io/api/akka/${Dependencies.AkkaBinaryVersion}",
        "javadoc.akka.base_url" -> s"https://doc.akka.io/japi/akka/${Dependencies.AkkaBinaryVersion}/",
        "javadoc.akka.link_style" -> "direct",
        "extref.akka-http.base_url" -> s"https://doc.akka.io/docs/akka-http/${Dependencies.AkkaHttpBinaryVersion}/%s",
        "scaladoc.akka.http.base_url" -> s"https://doc.akka.io/api/akka-http/${Dependencies.AkkaHttpBinaryVersion}/",
        "javadoc.akka.http.base_url" -> s"https://doc.akka.io/japi/akka-http/${Dependencies.AkkaHttpBinaryVersion}/",
        // Akka gRPC
        "akka-grpc.version" -> Dependencies.AkkaGrpcBinaryVersion,
        "extref.akka-grpc.base_url" -> s"https://doc.akka.io/docs/akka-grpc/${Dependencies.AkkaGrpcBinaryVersion}/%s",
        // Couchbase
        "couchbase.version" -> Dependencies.CouchbaseVersion,
        "extref.couchbase.base_url" -> s"https://docs.couchbase.com/java-sdk/${Dependencies.CouchbaseVersionForDocs}/%s",
        // Java
        "extref.java-api.base_url" -> "https://docs.oracle.com/javase/8/docs/api/index.html?%s.html",
        "extref.geode.base_url" -> s"https://geode.apache.org/docs/guide/${Dependencies.GeodeVersionForDocs}/%s",
        "extref.javaee-api.base_url" -> "https://docs.oracle.com/javaee/7/api/index.html?%s.html",
        "extref.paho-api.base_url" -> "https://www.eclipse.org/paho/files/javadoc/index.html?%s.html",
        "extref.pravega.base_url" -> s"https://cncf.pravega.io/docs/${Dependencies.PravegaVersionForDocs}/%s",
        "extref.slick.base_url" -> s"https://scala-slick.org/doc/${Dependencies.SlickVersion}/%s",
        // Cassandra
        "extref.cassandra.base_url" -> s"https://cassandra.apache.org/doc/${Dependencies.CassandraVersionInDocs}/%s",
        "extref.cassandra-driver.base_url" -> s"https://docs.datastax.com/en/developer/java-driver/${Dependencies.CassandraDriverVersionInDocs}/%s",
        "javadoc.com.datastax.oss.base_url" -> s"https://docs.datastax.com/en/drivers/java/${Dependencies.CassandraDriverVersionInDocs}/",
        // Solr
        "extref.solr.base_url" -> s"https://lucene.apache.org/solr/guide/${Dependencies.SolrVersionForDocs}/%s",
        "javadoc.org.apache.solr.base_url" -> s"https://lucene.apache.org/solr/${Dependencies.SolrVersionForDocs}_0/solr-solrj/",
        // Java
        "javadoc.base_url" -> "https://docs.oracle.com/javase/8/docs/api/",
        "javadoc.javax.jms.base_url" -> "https://docs.oracle.com/javaee/7/api/",
        "javadoc.com.couchbase.base_url" -> s"https://docs.couchbase.com/sdk-api/couchbase-java-client-${Dependencies.CouchbaseVersion}/",
        "javadoc.io.pravega.base_url" -> s"http://pravega.io/docs/${Dependencies.PravegaVersionForDocs}/javadoc/clients/",
        "javadoc.org.apache.kudu.base_url" -> s"https://kudu.apache.org/releases/${Dependencies.KuduVersion}/apidocs/",
        "javadoc.org.apache.hadoop.base_url" -> s"https://hadoop.apache.org/docs/r${Dependencies.HadoopVersion}/api/",
        "javadoc.software.amazon.awssdk.base_url" -> "https://sdk.amazonaws.com/java/api/latest/",
        "javadoc.com.google.auth.base_url" -> "https://www.javadoc.io/doc/com.google.auth/google-auth-library-credentials/latest/",
        "javadoc.com.google.auth.link_style" -> "direct",
        "javadoc.com.fasterxml.jackson.annotation.base_url" -> "https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-annotations/latest/",
        "javadoc.com.fasterxml.jackson.annotation.link_style" -> "direct",
        // Scala
        "scaladoc.spray.json.base_url" -> s"https://javadoc.io/doc/io.spray/spray-json_${scalaBinaryVersion.value}/latest/",
        // Eclipse Paho client for MQTT
        "javadoc.org.eclipse.paho.client.mqttv3.base_url" -> "https://www.eclipse.org/paho/files/javadoc/",
        "javadoc.org.bson.codecs.configuration.base_url" -> "https://mongodb.github.io/mongo-java-driver/3.7/javadoc/",
        "scaladoc.scala.base_url" -> s"https://www.scala-lang.org/api/${scalaBinaryVersion.value}.x/",
        "scaladoc.akka.stream.alpakka.base_url" -> s"/${(Preprocess / siteSubdirName).value}/",
        "javadoc.akka.stream.alpakka.base_url" -> ""
      ),
    paradoxGroups := Map("Language" -> Seq("Java", "Scala")),
    paradoxRoots := List("examples/elasticsearch-samples.html",
                         "examples/ftp-samples.html",
                         "examples/jms-samples.html",
                         "examples/mqtt-samples.html",
                         "index.html"),
    resolvers += Resolver.jcenterRepo,
    publishRsyncArtifacts += makeSite.value -> "www/",
    publishRsyncHost := "akkarepo@gustav.akka.io",
    apidocRootPackage := "akka"
  )

lazy val testkit = internalProject("testkit", Dependencies.testkit)

lazy val `doc-examples` = project
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(MimaPlugin, SitePlugin)
  .settings(
    name := s"akka-stream-alpakka-doc-examples",
    publish / skip := true,
    Dependencies.`Doc-examples`
  )

def pekkoConnectorProject(projectId: String,
                          moduleName: String,
                          additionalSettings: sbt.Def.SettingsDefinition*): Project = {
  import com.typesafe.tools.mima.core._
  Project(id = projectId, base = file(projectId))
    .enablePlugins(AutomateHeaderPlugin)
    .disablePlugins(SitePlugin)
    .settings(
      name := s"akka-stream-alpakka-$projectId",
      licenses := List(License.Apache2),
      AutomaticModuleName.settings(s"akka.stream.alpakka.$moduleName"),
      mimaPreviousArtifacts := Set(
          organization.value %% name.value % previousStableVersion.value
            .getOrElse("0.0.0")
        ),
      mimaBinaryIssueFilters ++= Seq(
          ProblemFilters.exclude[Problem]("*.impl.*"),
          // generated code
          ProblemFilters.exclude[Problem]("com.google.*")
        ),
      Test / parallelExecution := false
    )
    .settings(additionalSettings: _*)
    .dependsOn(testkit % Test)
}

def internalProject(projectId: String, additionalSettings: sbt.Def.SettingsDefinition*): Project =
  Project(id = projectId, base = file(projectId))
    .enablePlugins(AutomateHeaderPlugin)
    .disablePlugins(SitePlugin, MimaPlugin)
    .settings(
      name := s"akka-stream-alpakka-$projectId",
      publish / skip := true
    )
    .settings(additionalSettings: _*)

Global / onLoad := (Global / onLoad).value.andThen { s =>
  val v = version.value
  if (dynverGitDescribeOutput.value.hasNoTags)
    throw new MessageOnlyException(
      s"Failed to derive version from git tags. Maybe run `git fetch --unshallow`? Derived version: $v"
    )
  s
}
