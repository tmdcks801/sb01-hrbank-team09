package com.team09.sb01hrbank09.service;import java.time.Instant;import java.time.temporal.ChronoUnit;import java.util.ArrayList;import java.util.HashMap;import java.util.HashSet;import java.util.List;import java.util.Map;import java.util.Objects;import java.util.Set;import org.springframework.data.domain.Page;import org.springframework.data.domain.PageRequest;import org.springframework.data.domain.Pageable;import org.springframework.data.domain.Sort;import org.springframework.data.jpa.domain.Specification;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import com.fasterxml.jackson.core.JsonProcessingException;import com.fasterxml.jackson.databind.ObjectMapper;import com.team09.sb01hrbank09.dto.entityDto.ChangeLogDto;import com.team09.sb01hrbank09.dto.entityDto.DiffDto;import com.team09.sb01hrbank09.dto.request.CursorPageRequestChangeLog;import com.team09.sb01hrbank09.dto.response.CursorPageResponseChangeLogDto;import com.team09.sb01hrbank09.entity.ChangeLog;import com.team09.sb01hrbank09.mapper.ChangeLogMapper;import com.team09.sb01hrbank09.repository.ChangeLogRepository;import jakarta.persistence.EntityNotFoundException;import jakarta.persistence.criteria.Predicate;import lombok.RequiredArgsConstructor;@Service@RequiredArgsConstructor@Transactionalpublic class ChangeLogServiceImpl implements ChangeLogServiceInterface {	private final ChangeLogRepository changeLogRepository;	private final ChangeLogMapper changeLogMapper;	@Override	@Transactional(readOnly = true)	public CursorPageResponseChangeLogDto findChangeLogList(CursorPageRequestChangeLog request) {		// Pageable 생성		Pageable pageable = PageRequest.of(0, request.size(),			request.sortDirection().equalsIgnoreCase("desc") ? Sort.by(Sort.Order.desc(request.sortField())) :				Sort.by(Sort.Order.asc(request.sortField())));		// Specification 생성 (검색 조건 처리)		Specification<ChangeLog> spec = (root, query, criteriaBuilder) -> {			List<Predicate> predicates = new ArrayList<>();			if (request.employeeNumber() != null) {				predicates.add(criteriaBuilder.like(root.get("employeeNumber"), "%" + request.employeeNumber() + "%"));			}			if (request.memo() != null) {				predicates.add(criteriaBuilder.like(root.get("memo"), "%" + request.memo() + "%"));			}			if (request.ipAddress() != null) {				predicates.add(criteriaBuilder.like(root.get("ipAddress"), "%" + request.ipAddress() + "%"));			}			if (request.type() != null) {				predicates.add(criteriaBuilder.equal(root.get("type"), request.type()));			}			if (request.atFrom() != null && request.atTo() != null) {				predicates.add(criteriaBuilder.between(root.get("at"), request.atFrom(), request.atTo()));			}			return criteriaBuilder.and(predicates.toArray(new Predicate[0]));		};		// 커서 기반 페이지네이션 처리		Page<ChangeLog> page;		if (request.cursor() != null) {			Long idAfter = Long.parseLong(request.cursor());			page = changeLogRepository.findByIdGreaterThan(idAfter, pageable);		} else {			page = changeLogRepository.findAll(spec, pageable);		}		// DTO 변환 및 반환		List<ChangeLogDto> dtos = page.getContent().stream()			.map(changeLogMapper::changeLogToDto) // ChangeLog -> ChangeLogDto 변환			.toList();		return toCursorPageResponse(page, dtos);	}	@Override	@Transactional(readOnly = true)	public List<DiffDto> findChangeLogById(Long id) {		// 이력 조회		ChangeLog changeLog = changeLogRepository.findById(id)			.orElseThrow(() -> new EntityNotFoundException("ChangeLog not found for id: " + id));		// before, after JSON 비교하여 변경된 필드들 반환		return compareDiffs(changeLog.getBefore(), changeLog.getAfter());	}	@Override	@Transactional(readOnly = true)	public Long countChangeLog(Instant fromDate, Instant toDate) {		if (fromDate == null) {			fromDate = Instant.now().minus(7, ChronoUnit.DAYS); // 기본값: 7일 전		}		if (toDate == null) {			toDate = Instant.now(); // 기본값: 현재 시간		}		return changeLogRepository.countByAtBetween(fromDate, toDate);	}	private List<DiffDto> compareDiffs(String beforeJson, String afterJson) {		List<DiffDto> diffs = new ArrayList<>();		ObjectMapper objectMapper = new ObjectMapper();		try {			// beforeJson 또는 afterJson이 빈 객체로 저장되었으므로, null일 경우 빈 객체로 취급			Map<String, Object> beforeMap = (beforeJson != null && !beforeJson.isEmpty()) ?				objectMapper.readValue(beforeJson, Map.class) : new HashMap<>();			Map<String, Object> afterMap = (afterJson != null && !afterJson.isEmpty()) ?				objectMapper.readValue(afterJson, Map.class) : new HashMap<>();			// 모든 키를 비교			Set<String> allKeys = new HashSet<>();			allKeys.addAll(beforeMap.keySet());			allKeys.addAll(afterMap.keySet());			for (String key : allKeys) {				Object beforeValue = beforeMap.get(key);				Object afterValue = afterMap.get(key);				// before와 after가 다르면 DiffDto에 추가				if (!Objects.equals(beforeValue, afterValue)) {					diffs.add(new DiffDto(key, String.valueOf(beforeValue), String.valueOf(afterValue)));				}			}		} catch (JsonProcessingException e) {			throw new RuntimeException("JSON parsing error", e);		}		return diffs;	}	public static Specification<ChangeLog> buildSpecification(CursorPageRequestChangeLog request) {		return (root, query, criteriaBuilder) -> {			List<Predicate> predicates = new ArrayList<>();			if (request.employeeNumber() != null) {				predicates.add(criteriaBuilder.like(root.get("employeeNumber"), "%" + request.employeeNumber() + "%"));			}			if (request.memo() != null) {				predicates.add(criteriaBuilder.like(root.get("memo"), "%" + request.memo() + "%"));			}			if (request.ipAddress() != null) {				predicates.add(criteriaBuilder.like(root.get("ipAddress"), "%" + request.ipAddress() + "%"));			}			if (request.type() != null) {				predicates.add(criteriaBuilder.equal(root.get("type"), request.type()));			}			if (request.atFrom() != null && request.atTo() != null) {				predicates.add(criteriaBuilder.between(root.get("at"), request.atFrom(), request.atTo()));			}			return criteriaBuilder.and(predicates.toArray(new Predicate[0]));		};	}	// responseMapper 내부 구현	private CursorPageResponseChangeLogDto toCursorPageResponse(Page<ChangeLog> page, List<ChangeLogDto> dtos) {		String nextCursor =			page.hasNext() ? String.valueOf(page.getContent().get(page.getContent().size() - 1).getId()) : null;		Long nextIdAfter = page.hasNext() ? page.getContent().get(page.getContent().size() - 1).getId() : null;		return new CursorPageResponseChangeLogDto(			dtos,			nextCursor,			nextIdAfter,			page.getSize(),			page.getTotalElements(),			page.hasNext()		);	}}