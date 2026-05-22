package com.flighteasy.repository;

import com.fasterxml.jackson.databind.node.LongNode;
import com.flighteasy.entity.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PassengerRepository extends JpaRepository<Passenger, Long> {

    @Query("""
        SELECT COUNT (p) > 0 FROM Passenger p
        JOIN p.bookingSegment bs
        JOIN bs.flightClass fc
        WHERE fc.flight.id = :flightId
          AND p.idNumber IN :idNumbers
          AND bs.booking.status NOT IN ('CANCELLED', 'EXPIRED')
""")
    boolean existsDuplicateOnFlight(@Param("flightId") Long flightId,
                                    @Param("idNumbers")List<String> idNumbers);
}
