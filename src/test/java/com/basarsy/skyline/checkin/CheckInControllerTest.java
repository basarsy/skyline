package com.basarsy.skyline.checkin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.basarsy.skyline.checkin.repository.BoardingPassRepository;
import com.basarsy.skyline.fleet.entity.Aircraft;
import com.basarsy.skyline.fleet.entity.AircraftStatus;
import com.basarsy.skyline.fleet.repository.AircraftRepository;
import com.basarsy.skyline.fleet.repository.AircraftTypeRepository;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.Passenger;
import com.basarsy.skyline.reservation.entity.Reservation;
import com.basarsy.skyline.reservation.entity.ReservationStatus;
import com.basarsy.skyline.reservation.repository.PassengerRepository;
import com.basarsy.skyline.reservation.repository.ReservationRepository;
import com.basarsy.skyline.route.entity.Route;
import com.basarsy.skyline.route.repository.AirportRepository;
import com.basarsy.skyline.route.repository.RouteRepository;
import com.basarsy.skyline.schedule.entity.Flight;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import com.basarsy.skyline.schedule.repository.FlightRepository;
import com.basarsy.skyline.user.entity.User;
import com.basarsy.skyline.user.entity.UserRole;
import com.basarsy.skyline.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CheckInControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PassengerRepository passengerRepository;
    @Autowired private FlightRepository flightRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private BoardingPassRepository boardingPassRepository;
    @Autowired private AirportRepository airportRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private AircraftRepository aircraftRepository;
    @Autowired private AircraftTypeRepository aircraftTypeRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String userToken;
    private Reservation validReservation;

    @BeforeEach
    void setUp() throws Exception {
        boardingPassRepository.deleteAll();
        reservationRepository.deleteAll();
        passengerRepository.deleteAll();
        flightRepository.deleteAll();
        aircraftRepository.deleteAll();
        routeRepository.deleteAll();
        userRepository.deleteAll();

        // Setup User
        User user = new User();
        user.setEmail("passenger@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setRole(UserRole.PASSENGER);
        user.setEnabled(true);
        userRepository.save(user);

        userToken = login("passenger@example.com");

        // Setup Passenger
        Passenger passenger = new Passenger();
        passenger.setUser(user);
        passenger.setFirstName("John");
        passenger.setLastName("Doe");
        passenger.setPassportNumber("A1234567");
        passenger.setDateOfBirth(LocalDate.of(1990, 1, 1));
        passenger.setNationality("Turkish");
        passengerRepository.save(passenger);

        // Setup Route
        var airports = airportRepository.findAll();
        Route route = new Route();
        route.setOrigin(airports.get(0));
        route.setDestination(airports.get(1));
        route.setDistanceKm(1500);
        route.setEstimatedDurationMinutes(180);
        routeRepository.save(route);

        // Setup Flight (within check-in window: 24h from now)
        Flight flight = new Flight();
        flight.setFlightNumber("TK1903");
        flight.setRoute(route);
        flight.setAircraft(aircraftRepository.save(createAircraft()));
        flight.setDepartureTime(LocalDateTime.now().plusHours(24));
        flight.setArrivalTime(LocalDateTime.now().plusHours(28));
        flight.setStatus(FlightStatus.SCHEDULED);
        flight.setAvailableSeats(100);
        flight.setBasePrice(BigDecimal.valueOf(500));
        flightRepository.save(flight);

        // Setup Reservation
        validReservation = new Reservation();
        validReservation.setPassenger(passenger);
        validReservation.setFlight(flight);
        validReservation.setStatus(ReservationStatus.CONFIRMED);
        validReservation.setCabinClass(CabinClass.ECONOMY);
        validReservation.setTotalPrice(BigDecimal.valueOf(500));
        validReservation.setBookedAt(LocalDateTime.now().minusDays(1));
        validReservation.setPnr("ABC123");
        reservationRepository.save(validReservation);
    }

    private Aircraft createAircraft() {
        Aircraft aircraft = new Aircraft();
        aircraft.setTailNumber("TC-JPT");
        aircraft.setAircraftType(aircraftTypeRepository.findById(UUID.fromString("22222222-2222-2222-2222-222222222201")).get());
        aircraft.setStatus(AircraftStatus.ACTIVE);
        aircraft.setManufacturedYear(2022);
        return aircraft;
    }

    private String login(String email) throws Exception {
        var body = objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", "password123"));
        var result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    @Test
    void shouldPerformCheckInSuccessfully() throws Exception {
        var request = """
                {
                    "pnr": "ABC123"
                }
                """;

        mockMvc.perform(post("/api/v1/checkin")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pnr").value("ABC123"))
                .andExpect(jsonPath("$.data.passengerName").value("John Doe"))
                .andExpect(jsonPath("$.data.seatNumber").isNotEmpty());

        mockMvc.perform(get("/api/v1/checkin/ABC123/boarding-pass")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pnr").value("ABC123"));
    }

    @Test
    void shouldFailWhenOutsideCheckInWindow() throws Exception {
        // Create a flight 72 hours from now (too early)
        var flight = validReservation.getFlight();
        flight.setDepartureTime(LocalDateTime.now().plusHours(72));
        flightRepository.save(flight);

        var request = """
                {
                    "pnr": "ABC123"
                }
                """;

        mockMvc.perform(post("/api/v1/checkin")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Check-in opens 48 hours before departure."));
    }
}
