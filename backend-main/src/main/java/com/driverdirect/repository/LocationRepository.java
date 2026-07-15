package com.driverdirect.repository;

import com.driverdirect.model.Shipper;
import com.driverdirect.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findByOwnerShipper(Shipper shipper);
    Optional<Location> findFirstByNameIgnoreCaseAndCountry(String name, String country);

    /**
     * The locations a shipper may route between: public reference nodes
     * (typed ports/airports/rail terminals — anything that isn't a plain
     * ADDRESS) plus the shipper's own locations. Mirrors the tenant-scoping
     * rule in {@code RoutePlannerService.requireAccessible}, so the route
     * planner's picker only offers ids the plan/accept endpoints will accept.
     */
    @Query("select l from Location l where l.locationType <> :addressType "
            + "or l.ownerShipper = :owner order by l.name asc")
    List<Location> findRoutableForShipper(@Param("addressType") Location.LocationType addressType,
                                          @Param("owner") Shipper owner);
}
