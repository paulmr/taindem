package taindem

trait TaindemLogging extends slogging.LazyLogging {
  slogging.LoggerConfig.factory = slogging.ConsoleLoggerFactory()
}
