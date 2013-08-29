package com.pongr.metrics.amazoncloudwatch

import com.codahale.metrics._
import grizzled.slf4j.Logging

import java.util.Date
import java.util.concurrent.TimeUnit
import scala.collection.JavaConversions._

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient
import com.amazonaws.services.cloudwatch.model.{ StandardUnit, PutMetricDataRequest, MetricDatum, Dimension, StatisticSet }

case class AmazonCloudWatch(
  accessKeyId: String,
  secretKey  : String,
  nameSpace  : String,
  dimensions : Map[String, String]
) extends Logging {

  val dims     = dimensions.map { case (k, v) => (new Dimension).withName(k).withValue(v) }
  val awsCreds = new BasicAWSCredentials(accessKeyId, secretKey)
  val client   = new AmazonCloudWatchClient(awsCreds)


  def timestamp = System.currentTimeMillis

  def toStandardUnit(timeUnit: Option[TimeUnit]): StandardUnit = timeUnit.map(toStandardUnit) getOrElse StandardUnit.None

  def toStandardUnit(timeUnit: TimeUnit): StandardUnit = timeUnit match {
    case TimeUnit.NANOSECONDS  => StandardUnit.Microseconds
    case TimeUnit.MILLISECONDS => StandardUnit.Milliseconds
    case TimeUnit.SECONDS      => StandardUnit.Seconds
    case TimeUnit.MINUTES      => StandardUnit.None
    case TimeUnit.HOURS        => StandardUnit.None
    case TimeUnit.DAYS         => StandardUnit.None
  }

  def sendStats(name: String, stats: StatisticSet, timestamp: Long, timeUnit: Option[TimeUnit] = None) {
    if (stats.getSum != 0 && stats.getSampleCount != 0) {
      val metricData = (new MetricDatum()).withDimensions(dims)
                                          .withMetricName(name)
                                          .withStatisticValues(stats)
                                          .withTimestamp(new Date(timestamp))
                                          .withUnit(toStandardUnit(timeUnit))
      send(metricData)
    }
    else
      debug("%s value is 0, cannot be sent to CloudWatch." format name)
  }

  def sendValue(name: String, value: Double, timestamp: Long, timeUnit: Option[TimeUnit] = None) {
    if (value != 0d) {
      val metricData = (new MetricDatum()).withDimensions(dims)
                                          .withMetricName(name)
                                          .withValue(value)
                                          .withTimestamp(new Date(timestamp))
                                          .withUnit(toStandardUnit(timeUnit))
      send(metricData)
    }
    else
      debug("%s value is 0, cannot be sent to CloudWatch." format name)
  }

  def send(metricData: MetricDatum) {
    try {
      val metricRequest = (new PutMetricDataRequest).withMetricData(metricData).withNamespace(nameSpace)
      client.putMetricData(metricRequest)
    }
    catch { case e =>
      warn(e)
    }
  }

}
