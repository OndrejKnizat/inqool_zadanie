package sk.knizat.tennisclub.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.knizat.tennisclub.dao.SurfaceTypeDao;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeRequest;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeResponse;
import sk.knizat.tennisclub.entity.SurfaceType;
import sk.knizat.tennisclub.exception.ConflictException;
import sk.knizat.tennisclub.exception.NotFoundException;
import sk.knizat.tennisclub.mapper.SurfaceTypeMapper;
import sk.knizat.tennisclub.service.SurfaceTypeService;

import java.time.Clock;
import java.util.List;

/** Default {@link SurfaceTypeService} backed by {@link SurfaceTypeDao}. */
@Service
@Transactional
@RequiredArgsConstructor
public class SurfaceTypeServiceImpl implements SurfaceTypeService {

    private static final String ENTITY = "SurfaceType";

    private final SurfaceTypeDao surfaceTypeDao;
    private final SurfaceTypeMapper mapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<SurfaceTypeResponse> findAll() {
        return surfaceTypeDao.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SurfaceTypeResponse findById(Long id) {
        return mapper.toResponse(getOrThrow(id));
    }

    @Override
    public SurfaceTypeResponse create(SurfaceTypeRequest request) {
        String name = mapper.normaliseName(request.name());
        if (surfaceTypeDao.findByName(name).isPresent()) {
            throw duplicateName(name);
        }
        SurfaceType saved = surfaceTypeDao.save(mapper.toEntity(request));
        return mapper.toResponse(saved);
    }

    @Override
    public SurfaceTypeResponse update(Long id, SurfaceTypeRequest request) {
        SurfaceType entity = getOrThrow(id);
        String name = mapper.normaliseName(request.name());
        boolean takenByOther = surfaceTypeDao.findByName(name)
                .map(other -> !other.getId().equals(id))
                .orElse(false);
        if (takenByOther) {
            throw duplicateName(name);
        }
        mapper.updateEntity(entity, request);
        return mapper.toResponse(surfaceTypeDao.save(entity));
    }

    @Override
    public void delete(Long id) {
        SurfaceType entity = getOrThrow(id);
        long courts = surfaceTypeDao.countCourtsUsing(id);
        if (courts > 0) {
            throw new ConflictException(
                    "SurfaceType with id " + id + " is used by " + courts + " court(s) and cannot be deleted");
        }
        surfaceTypeDao.softDelete(entity, clock.instant());
    }

    private SurfaceType getOrThrow(Long id) {
        return surfaceTypeDao.findById(id).orElseThrow(() -> NotFoundException.of(ENTITY, id));
    }

    private static ConflictException duplicateName(String name) {
        return new ConflictException("SurfaceType with name '" + name + "' already exists");
    }
}
