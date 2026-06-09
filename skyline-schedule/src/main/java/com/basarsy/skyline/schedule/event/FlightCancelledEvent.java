package com.basarsy.skyline.schedule.event;

import java.util.UUID;

public record FlightCancelledEvent(UUID flightId) {}
