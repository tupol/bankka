package org.tupol.takkagotchi.being

import java.time.Instant

case class Record(entry: String, timestamp: Instant = Instant.now())
