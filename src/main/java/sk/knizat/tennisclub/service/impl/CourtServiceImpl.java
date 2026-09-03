package sk.knizat.tennisclub.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.knizat.tennisclub.dao.CourtDao;
import sk.knizat.tennisclub.dao.ReservationDao;
import sk.knizat.tennisclub.dao.SurfaceTypeDao;
import sk.knizat.tennisclub.dto.court.CourtRequest;
import sk.knizat.tennisclub.dto.court.CourtResponse;
import sk.knizat.tennisclub.entity.Court;
import sk.knizat.tennisclub.entity.SurfaceType;
import sk.knizat.tennisclub.exception.ConflictException;
import sk.knizat.tennisclub.exception.NotFoundException;
import sk.knizat.tennisclub.exception.ValidationException;
import sk.knizat.tennisclub.mapper.CourtMapper;
import sk.knizat.tennisclub.service.CourtService;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Default {@link CourtService} backed by {@link CourtDao}. */
@Service
@Transactional
@RequiredArgsConstructor
public class CourtServiceImpl implements CourtService {

    private static final String ENTITY = "Court";

    private final CourtDao courtDao;
    private final SurfaceTypeDao surfaceTypeDao;
    private final ReservationDao reservationDao;
    private final CourtMapper mapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<CourtResponse> findAll() {
        return courtDao.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CourtResponse findById(Long id) {
        return mapper.toResponse(getOrThrow(id));
    }

    @Override
    public CourtResponse create(CourtRequest request) {
        if (courtDao.existsByCourtNumber(request.courtNumber())) {
            throw duplicateNumber(request.courtNumber());
        }
        SurfaceType surfaceType = resolveSurfaceType(request.surfaceTypeId());
        Court saved = courtDao.save(mapper.toEntity(request, surfaceType));
        return mapper.toResponse(saved);
    }

    @Override
    public CourtResponse update(Long id, CourtRequest request) {
        Court entity = getOrThrow(id);
        boolean takenByOther = courtDao.findByCourtNumber(request.courtNumber())
                .map(other -> !other.getId().equals(id))
                .orElse(false);
        if (takenByOther) {
            throw duplicateNumber(request.courtNumber());
        }
        SurfaceType surfaceType = resolveSurfaceType(request.surfaceTypeId());
        mapper.updateEntity(entity, request, surfaceType);
        return mapper.toResponse(courtDao.save(entity));
    }

    @Override
    public void delete(Long id) {
        Court entity = getOrThrow(id);
        Instant now = clock.instant();
        if (reservationDao.existsUnfinishedByCourt(id, now)) {
            throw new ConflictException(
                    "Court with id " + id + " has unfinished reservations and cannot be deleted");
        }
        courtDao.softDelete(entity, now);
    }

    private Court getOrThrow(Long id) {
        return courtDao.findById(id).orElseThrow(() -> NotFoundException.of(ENTITY, id));
    }

    /** The surface type id comes from the payload, so an unknown or deleted one is a 400, not a 404. */
    private SurfaceType resolveSurfaceType(Long surfaceTypeId) {
        return surfaceTypeDao.findById(surfaceTypeId)
                .orElseThrow(() -> new ValidationException("surfaceTypeId " + surfaceTypeId + " does not exist"));
    }

    private static ConflictException duplicateNumber(Integer courtNumber) {
        return new ConflictException("Court with number " + courtNumber + " already exists");
    }
}
