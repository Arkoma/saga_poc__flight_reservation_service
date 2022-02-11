package com.saga.saga_poc__flight_reservation_service.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saga.saga_poc__flight_reservation_service.model.Flight;
import com.saga.saga_poc__flight_reservation_service.model.FlightReservation;
import com.saga.saga_poc__flight_reservation_service.model.FlightReservationRequest;
import com.saga.saga_poc__flight_reservation_service.model.StatusEnum;
import com.saga.saga_poc__flight_reservation_service.repository.FlightRepository;
import com.saga.saga_poc__flight_reservation_service.repository.FlightReservationRepository;
import com.saga.saga_poc__flight_reservation_service.service.FlightReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.transaction.Transactional;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@WebAppConfiguration
class FlightReservationControllerIT {

    private final WebApplicationContext webApplicationContext;
    private final FlightReservationService flightReservationService;
    private final FlightRepository flightRepository;
    private final FlightReservationRepository flightReservationRepository;
    private final FlightReservationController underTest;

    @Autowired
    public FlightReservationControllerIT(WebApplicationContext webApplicationContext,
                                         FlightReservationService flightReservationService,
                                         FlightReservationController flightReservationController,
                                         FlightRepository flightRepository,
                                         FlightReservationRepository flightReservationRepository) throws ParseException {
        this.webApplicationContext = webApplicationContext;
        this.flightReservationService = flightReservationService;
        this.underTest = flightReservationController;
        this.flightRepository = flightRepository;
        this.flightReservationRepository = flightReservationRepository;
    }

    private MockMvc mockMvc;
    private FlightReservationRequest flightReservationRequest;
    private Flight hotel;
    private final Long reservationId = 1L;
    private final int roomNumber = 666;
    private final Date checkinDate = new SimpleDateFormat("d MMM yyyy").parse("9 Feb 2022");
    private final Date checkoutDate = new SimpleDateFormat("dd MMM yyyy").parse("12 Feb 2022");
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
        hotel = new Flight();
        hotel.setFlightNumber("Holiday Inn");
        this.flightRepository.deleteAll();
        hotel = this.flightRepository.save(hotel);
        flightReservationRequest = FlightReservationRequest.builder()
                .reservationId(reservationId)
                .flight(hotel)
                .flightNumber(roomNumber)
                .seatNumber(checkinDate)
                .departureDate(checkoutDate)
                .build();
        this.flightReservationRepository.deleteAll();
    }

    @Test
    void hotelReservationControllerExistsAsABean() {
        assertTrue(webApplicationContext.containsBean("flightReservationController"));
    }

    @Test
    void hotelReservationServiceIsInjectedInTheController() {
        FlightReservationService injectedFlightReservationService =(FlightReservationService) ReflectionTestUtils.getField(underTest, "flightReservationService");
        assertSame(flightReservationService, injectedFlightReservationService);
    }

    @Test
    void makeReservationEndpointExists() throws Exception {
        String json = mapper.writeValueAsString(this.flightReservationRequest);
        this.mockMvc.perform(post("/reservation").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    @Test
    void makeReservationEndpointReturnsReservation() throws Exception {
        final MvcResult result = this.makeReservation();
        String responseJson = result.getResponse().getContentAsString();
        FlightReservation actualResponse = mapper.readValue(responseJson, FlightReservation.class);
        assertAll(() -> {
            assertEquals(StatusEnum.RESERVED, actualResponse.getStatus());
            assertEquals(hotel.getId(), actualResponse.getFlightId());
            assertEquals(this.reservationId, actualResponse.getReservationId());
            assertEquals(this.checkinDate, actualResponse.getCheckinDate());
            assertEquals(this.checkoutDate, actualResponse.getDepartureDate());
            assertEquals(this.roomNumber, actualResponse.getSeatNumber());
                }
        );
    }

    private MvcResult makeReservation() throws Exception {
        String json = mapper.writeValueAsString(this.flightReservationRequest);
        return this.mockMvc.perform(post("/reservation").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpectAll(
                        status().isCreated(),
                        content().contentType(MediaType.APPLICATION_JSON)
                )
                .andReturn();
    }

    @Test
    @Transactional
    void makeReservationEndpointSavesReservation() throws Exception {
        final MvcResult result = this.makeReservation();
        String responseJson = result.getResponse().getContentAsString();
        FlightReservation actualResponse = mapper.readValue(responseJson, FlightReservation.class);
        FlightReservation actualEntity = this.flightReservationRepository.getById(actualResponse.getId());
        assertAll(() -> {
            assertEquals(StatusEnum.RESERVED ,actualEntity.getStatus());
            assertEquals(hotel.getId(), actualEntity.getFlightId());
            assertEquals(this.reservationId, actualEntity.getReservationId());
            assertEquals(this.checkinDate, actualEntity.getCheckinDate());
            assertEquals(this.checkoutDate, actualEntity.getDepartureDate());
            assertEquals(this.roomNumber, actualEntity.getSeatNumber());
                }
        );
    }

    @Test
    void cancelReservationEndpointExists() throws Exception {
        final MvcResult result = this.makeReservation();
        String responseJson = result.getResponse().getContentAsString();
        FlightReservation reservation = mapper.readValue(responseJson, FlightReservation.class);
        Long id = reservation.getId();
        this.mockMvc.perform(delete("/reservation/" + id))
                .andExpect(status().isNoContent());
    }

    @Test
    @Transactional
    void cancelReservationEndpointRemovesReservation() throws Exception {
        final MvcResult result = this.makeReservation();
        String responseJson = result.getResponse().getContentAsString();
        FlightReservation reservation = mapper.readValue(responseJson, FlightReservation.class);
        Long id = reservation.getId();
        FlightReservation reservationFromDb = this.flightReservationRepository.getById(id);
        assertEquals(id, reservationFromDb.getId());
        this.mockMvc.perform(delete("/reservation/" + id))
                .andExpect(status().isNoContent());
        reservationFromDb = this.flightReservationRepository.findById(id).orElse(null);
        assertNull(reservationFromDb);
    }
    
    @Test
    void getReservationEndpointExists() throws Exception {
        final MvcResult result = this.makeReservation();
        String responseJson = result.getResponse().getContentAsString();
        FlightReservation reservation = mapper.readValue(responseJson, FlightReservation.class);
        Long id = reservation.getId();
        final MvcResult foundResult = this.mockMvc.perform(get("/reservation/" + id))
            .andExpectAll(
                    status().isOk(),
                    content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        String foundResponseJson = foundResult.getResponse().getContentAsString();
        FlightReservation foundReservation = mapper.readValue(foundResponseJson, FlightReservation.class);
        assertAll(() -> {
                    assertEquals(StatusEnum.RESERVED ,foundReservation.getStatus());
                    assertEquals(this.hotel.getId(), foundReservation.getFlightId());
                    assertEquals(this.reservationId, foundReservation.getReservationId());
                    assertEquals(this.checkinDate, foundReservation.getCheckinDate());
                    assertEquals(this.checkoutDate, foundReservation.getDepartureDate());
                    assertEquals(this.roomNumber, foundReservation.getSeatNumber());
                }
        );
    }

    @Test
    void getReservationReturnsNotFoundIfReservationDoesNotExist() throws Exception {
        this.mockMvc.perform(get("/reservation/" + 1L))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetAll() throws Exception {
        this.makeReservation();
        this.makeReservation();
        final MvcResult foundResult = this.mockMvc.perform(get("/reservations"))
                .andExpectAll(
                        status().isOk(),
                        content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String foundResponseJson = foundResult.getResponse().getContentAsString();
        List<FlightReservation> foundReservations = mapper.readValue(foundResponseJson, new TypeReference<List<FlightReservation>>(){});
        assertAll(() -> {
                    assertEquals(StatusEnum.RESERVED ,foundReservations.get(0).getStatus());
                    assertEquals(this.hotel.getId(), foundReservations.get(0).getFlightId());
                    assertEquals(this.reservationId, foundReservations.get(0).getReservationId());
                    assertEquals(this.checkinDate, foundReservations.get(0).getCheckinDate());
                    assertEquals(this.checkoutDate, foundReservations.get(0).getDepartureDate());
                    assertEquals(this.roomNumber, foundReservations.get(0).getSeatNumber());
                    assertEquals(2, foundReservations.size());
                }
        );
    }

}
