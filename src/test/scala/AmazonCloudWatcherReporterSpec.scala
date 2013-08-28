package com.pongr.metrics.amazoncloudwatch

import org.specs2.mutable._
import org.specs2.mock._
import scala.collection.JavaConversions._

import java.util.concurrent.TimeUnit
import java.util.{ SortedMap, TreeMap }

import com.codahale.metrics._
import com.amazonaws.services.cloudwatch.model.StatisticSet
import org.mockito.Mockito.doNothing

class AmazonCloudWatchReporterSpec extends Specification with Mockito {

  def map[T] = new TreeMap[String, T]

  def map[T](name: String, metric: T) = {
    val m: SortedMap[String, T] = new TreeMap
    m.put(name, metric)
    m
  }

  def gauge[T](value: T) = new Gauge[T] { def getValue = value }

  trait TestData extends Before {
    val timestamp = 1000l
    val cloudWatch = mock[AmazonCloudWatch]
    val registry = mock[MetricRegistry]
    val rateUnit = TimeUnit.SECONDS
    val durationUnit = TimeUnit.MILLISECONDS
    val counterValue = 100l

    cloudWatch.timestamp returns timestamp

    val counter = mock[Counter]
    counter.getCount returns counterValue
    
    val reporter = new AmazonCloudWatchReporter(cloudWatch, registry, rateUnit, durationUnit)
    def before = {}
  }

  "AmazonCloudWathReporter" should {

    "report gauge values by default" in new TestData {
      reporter.report(map("gauge", gauge(123)), map[Counter], map[Histogram], map[Meter], map[Timer])
      there was one(cloudWatch).sendValue("gauge", 123d, timestamp, null) 
    }

    "don't report gauge when disabled" in new TestData {
      val cwr = new AmazonCloudWatchReporter(cloudWatch, registry, rateUnit, durationUnit, sendGauge = false)
      cwr.report(map("gauge", gauge(123)), map[Counter], map[Histogram], map[Meter], map[Timer])
      there was no(cloudWatch).sendValue("gauge", 123d, timestamp, null) 
    }

    "report counter values by default" in new TestData {
      reporter.report(map[Gauge[_]], map("counter", counter), map[Histogram], map[Meter], map[Timer])
      there was one(cloudWatch).sendValue("counter", counterValue, timestamp, null) 
    }

    "don't report counter values when disabled" in new TestData {
      val cwr = new AmazonCloudWatchReporter(cloudWatch, registry, rateUnit, durationUnit, sendCounter = false)
      cwr.report(map[Gauge[_]], map("counter", counter), map[Histogram], map[Meter], map[Timer])
      there was no(cloudWatch).sendValue("counter", counterValue, timestamp, null) 
    }

    "report rates when enabled" in new TestData {
      val meter = mock[Meter]
      meter.getMeanRate returns 8d
      meter.getOneMinuteRate returns 1
      meter.getFiveMinuteRate returns 5
      meter.getFifteenMinuteRate returns 15

      val cwr = new AmazonCloudWatchReporter(cloudWatch, registry, rateUnit, durationUnit, 
        sendMeanRate = true,
        sendOneMinuteRate = true,
        sendFiveMinuteRate = true,
        sendFifteenMinuteRate = true
      )
      cwr.reportRates("meter", meter, timestamp)

      there was one(cloudWatch).sendValue("meter.mean_rate", 8d, timestamp, Some(rateUnit))
      there was one(cloudWatch).sendValue("meter.m1_rate", 1d, timestamp, Some(rateUnit))
      there was one(cloudWatch).sendValue("meter.m5_rate", 5d, timestamp, Some(rateUnit))
      there was one(cloudWatch).sendValue("meter.m15_rate", 15d, timestamp, Some(rateUnit))
    }

    "don't report rates by default" in new TestData {
      val meter = mock[Meter]
      reporter.reportRates("meter", meter, timestamp)
      there was no(cloudWatch).sendValue(anyString, anyDouble, anyLong, any)
    }

    "report snapshot stats only by default" in new TestData {
      val snapshot = mock[Snapshot]

      snapshot.getMax() returns 6l
      snapshot.getMean() returns 3.0
      snapshot.getMin() returns 4l
      snapshot.getStdDev() returns 5.0
      snapshot.getMedian() returns 6.0
      snapshot.get75thPercentile() returns 7.0
      snapshot.get95thPercentile() returns 8.0
      snapshot.get98thPercentile() returns 9.0
      snapshot.get99thPercentile() returns 10.0
      snapshot.get999thPercentile() returns 11.0
      snapshot.getValues returns Array(6l, 4l)

      reporter.reportSnapshot("snapshot", snapshot, timestamp)

      val stats = (new StatisticSet).withMaximum(6l)
                                    .withMinimum(4l)
                                    .withSampleCount(2)
                                    .withSum(10l)

      there was one(cloudWatch).sendStats("snapshot", stats, timestamp, None)
    }

    "report snapshot stats and percentiles when enabled" in new TestData {
      val snapshot = mock[Snapshot]
      val cwr = new AmazonCloudWatchReporter(cloudWatch, registry, rateUnit, durationUnit, percentilesToSend = Percentile.all)

      snapshot.getMax() returns 6l
      snapshot.getMean() returns 3.0
      snapshot.getMin() returns 4l
      snapshot.getStdDev() returns 5.0
      snapshot.getMedian() returns 6.0
      snapshot.get75thPercentile() returns 7.0
      snapshot.get95thPercentile() returns 8.0
      snapshot.get98thPercentile() returns 9.0
      snapshot.get99thPercentile() returns 10.0
      snapshot.get999thPercentile() returns 11.0
      snapshot.getValues returns Array(6l, 4l)
      snapshot.getMedian returns 50d
      snapshot.get75thPercentile returns 75d
      snapshot.get95thPercentile returns 95d
      snapshot.get98thPercentile returns 98d
      snapshot.get99thPercentile returns 99d
      snapshot.get999thPercentile returns 999d

      cwr.reportSnapshot("snapshot", snapshot, timestamp)

      val stats = (new StatisticSet).withMaximum(6l)
                                    .withMinimum(4l)
                                    .withSampleCount(2)
                                    .withSum(10l)

      there was one(cloudWatch).sendStats("snapshot", stats, timestamp, None)
      there was one(cloudWatch).sendValue("snapshot.p50", 50d, timestamp, None)
      there was one(cloudWatch).sendValue("snapshot.p75", 75d, timestamp, None)
      there was one(cloudWatch).sendValue("snapshot.p95", 95d, timestamp, None)
      there was one(cloudWatch).sendValue("snapshot.p98", 98d, timestamp, None)
      there was one(cloudWatch).sendValue("snapshot.p99", 99d, timestamp, None)
      there was one(cloudWatch).sendValue("snapshot.p999", 999d, timestamp, None)
    }

    "report timer values by default" in new TestData {
      val cwr = spy(reporter)
      val timer = mock[Timer]
      val snapshot = mock[Snapshot]

      timer.getSnapshot returns snapshot

      doNothing().when(cwr).reportSnapshot(anyString, any, anyLong, any)
      doNothing().when(cwr).reportRates(anyString, any, anyLong)

      cwr.reportTimer("timer", timer, timestamp)

      there was one(cwr).reportSnapshot("timer", snapshot, timestamp, Some(durationUnit))
      there was one(cwr).reportRates("timer", timer, timestamp)
    }

    "don't report timer values if disabled" in new TestData {
      val timer = mock[Timer]
      val cwr = spy(new AmazonCloudWatchReporter(cloudWatch, registry, rateUnit, durationUnit, sendTimer = false))
      cwr.report(map[Gauge[_]], map[Counter], map[Histogram], map[Meter], map("timer", timer))
      there was no(cwr).reportTimer("timer", timer, timestamp) 
    }

    "report meter values by default" in new TestData {
      val meter = mock[Meter]
      meter.getCount returns 12
      val cwr = spy(new AmazonCloudWatchReporter(cloudWatch, registry, rateUnit, durationUnit))
      cwr.report(map[Gauge[_]], map[Counter], map[Histogram], map("meter", meter), map[Timer])
      there was one(cloudWatch).sendValue("meter.count", 12, timestamp, Some(rateUnit)) 
      there was one(cwr).reportRates("meter", meter, timestamp)
    }

  }

}
