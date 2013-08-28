package com.pongr.metrics.amazoncloudwatch

import com.codahale.metrics._
import grizzled.slf4j.Logging

import java.util.SortedMap
import java.util.concurrent.TimeUnit
import scala.collection.JavaConversions._

import com.amazonaws.services.cloudwatch.model.StatisticSet

object Percentile extends Enumeration {
  type Type = Value
  val P50, P75, P95, P98, P99, P999 = Value
  def all = List(P50, P75, P95, P98, P99, P999)
}

class AmazonCloudWatchReporter(
  val amazonCloudWatch: AmazonCloudWatch,
  val registry: MetricRegistry,
  val rateUnit: TimeUnit,
  val durationUnit: TimeUnit,
  val filter: MetricFilter = MetricFilter.ALL,
  val sendTimer            : Boolean = true,
  val sendCounter          : Boolean = true,
  val sendGauge            : Boolean = true,
  val sendHistogram        : Boolean = true,
  val sendOneMinuteRate    : Boolean = false,
  val sendFiveMinuteRate   : Boolean = false,
  val sendFifteenMinuteRate: Boolean = false,
  val sendMeanRate         : Boolean = false,
  val sendStdDev           : Boolean = false,
  val percentilesToSend    : List[Percentile.Type] = Nil
) extends ScheduledReporter (registry, "amazon-cloudwatch-reporter", filter, rateUnit, durationUnit) with Logging {

  import Percentile._
  import amazonCloudWatch._

  override def report (
    gauges    : SortedMap[String, Gauge[_]],
    counters  : SortedMap[String, Counter],
    histograms: SortedMap[String, Histogram],
    meters    : SortedMap[String, Meter],
    timers    : SortedMap[String, Timer]
  ) {

    val timestamp = amazonCloudWatch.timestamp

    for (kv <- gauges.entrySet     if (sendGauge))     reportGauge    (kv.getKey, kv.getValue, timestamp)
    for (kv <- counters.entrySet   if (sendCounter))   reportCounter  (kv.getKey, kv.getValue, timestamp)
    for (kv <- histograms.entrySet if (sendHistogram)) reportHistogram(kv.getKey, kv.getValue, timestamp)
    for (kv <- timers.entrySet     if (sendTimer))     reportTimer    (kv.getKey, kv.getValue, timestamp)
    for (kv <- meters.entrySet)                        reportMeter    (kv.getKey, kv.getValue, timestamp)
  }

  def reportSnapshot(name: String, snapshot: Snapshot, timestamp: Long, timeUnit: Option[TimeUnit] = None) {
    def convert(value: Double) = if (timeUnit.isDefined) convertDuration(value) else value

    val stats = (new StatisticSet).withMaximum(convert(snapshot.getMax))
                                  .withMinimum(convert(snapshot.getMin))
                                  .withSampleCount(snapshot.getValues.size)
                                  .withSum(snapshot.getValues.map(convert(_)).foldLeft(0d)(_ + _))

    sendStats(name, stats, timestamp, timeUnit)

    if (sendStdDev) sendValue(name + ".stddev", convert(snapshot.getStdDev), timestamp, timeUnit)

    if (percentilesToSend.contains(P50))  sendValue(name + ".p50", convert(snapshot.getMedian), timestamp, timeUnit)
    if (percentilesToSend.contains(P75))  sendValue(name + ".p75", convert(snapshot.get75thPercentile), timestamp, timeUnit)
    if (percentilesToSend.contains(P95))  sendValue(name + ".p95", convert(snapshot.get95thPercentile), timestamp, timeUnit)
    if (percentilesToSend.contains(P98))  sendValue(name + ".p98", convert(snapshot.get98thPercentile), timestamp, timeUnit)
    if (percentilesToSend.contains(P99))  sendValue(name + ".p99", convert(snapshot.get99thPercentile), timestamp, timeUnit)
    if (percentilesToSend.contains(P999)) sendValue(name + ".p999", convert(snapshot.get999thPercentile), timestamp, timeUnit)
  }

  def reportTimer(name: String, timer: Timer, timestamp: Long) {
    reportSnapshot(name, timer.getSnapshot, timestamp, Some(durationUnit))
    reportRates(name, timer, timestamp)
  }

  def reportCounter(name: String, counter: Counter, timestamp: Long) {
    sendValue(name, counter.getCount, timestamp)
  }

  def reportGauge(name: String, gauge: Gauge[_], timestamp: Long) {
    if (sendGauge) sendValue(name, gauge.getValue.asInstanceOf[Number].doubleValue, timestamp)
  }

  def reportHistogram(name: String, histogram: Histogram, timestamp: Long) {
    reportSnapshot(name, histogram.getSnapshot, timestamp)
  }

  def reportMeter(name: String, meter: Metered, timestamp: Long) {
    sendValue(name + ".count", meter.getCount, timestamp, Some(rateUnit))
    reportRates(name, meter, timestamp)
  }

  def reportRates(name: String, meter: Metered, timestamp: Long) {
    if (sendMeanRate)
      sendValue(name + ".mean_rate", convertRate(meter.getMeanRate), timestamp, Some(rateUnit))

    if (sendOneMinuteRate)
      sendValue(name + ".m1_rate", convertRate(meter.getOneMinuteRate), timestamp, Some(rateUnit))

    if (sendFiveMinuteRate)
      sendValue(name + ".m5_rate", convertRate(meter.getFiveMinuteRate), timestamp, Some(rateUnit))

    if (sendFiveMinuteRate)
      sendValue(name + ".m15_rate", convertRate(meter.getFifteenMinuteRate), timestamp, Some(rateUnit))
  }

}
