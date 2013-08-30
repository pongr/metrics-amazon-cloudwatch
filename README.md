Metrics for CloudWatch
=========================

Sends [Metrics](https://github.com/codahale/metrics/) reporting to [Amazon's CloudWatch](http://aws.amazon.com/cloudwatch/).

### sbt

Snapshots are in [Sonatype repository](https://oss.sonatype.org/content/repositories/snapshot/).
```
"com.pongr" %% "metrics-amazon-cloudwatch" % "0.1-SNAPSHOT"
```

### Usage

```scala
import com.codahale.metrics.MetricRegistry
import java.util.concurrent.TimeUnit
import com.pongr.metrics.amazoncloudwatch._

val metricRegistry = new MetricRegistry()

val cloudWatch = new AmazonCloudWatch(
  accessKeyId = "access-key-id",
  secretKey   = "secret-key",
  nameSpace   = "your namespace",
  dimensions  = Map("host" -> "localhost")
)
val reporter = new AmazonCloudWatchReporter(cloudWatch, metricRegistry, TimeUnit.SECONDS, TimeUnit.MILLISECONDS)
reporter.start(20, TimeUnit.SECONDS)

```

### License

metrics-amazon-cloudwatch is licensed under the [Apache 2 License](http://www.apache.org/licenses/LICENSE-2.0.txt).
