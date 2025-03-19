package com.team09.sb01hrbank09.service;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.team09.sb01hrbank09.dto.entityDto.EmployeeDistributionDto;
import com.team09.sb01hrbank09.dto.entityDto.EmployeeDto;
import com.team09.sb01hrbank09.dto.entityDto.EmployeeTrendDto;
import com.team09.sb01hrbank09.dto.request.EmployeeCreateRequest;
import com.team09.sb01hrbank09.dto.request.EmployeeUpdateRequest;
import com.team09.sb01hrbank09.dto.response.CursorPageResponseEmployeeDto;
import com.team09.sb01hrbank09.entity.Department;
import com.team09.sb01hrbank09.entity.Employee;
import com.team09.sb01hrbank09.entity.Enum.ChangeLogType;
import com.team09.sb01hrbank09.entity.Enum.EmployeeStatus;
import com.team09.sb01hrbank09.entity.File;
import com.team09.sb01hrbank09.event.EmployeeEvent;
import com.team09.sb01hrbank09.mapper.EmployeeMapper;
import com.team09.sb01hrbank09.repository.EmployeeRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EmployeeServiceImpl implements EmployeeServiceInterface {

	private final EmployeeRepository employeeRepository;
	private final DepartmentServiceInterface departmentServiceInterface;
	private final FileServiceInterface fileServiceInterface;
	private final ChangeLogServiceInterface changeLogServiceInterface;
	private final EmployeeMapper employeeMapper;
	private final ApplicationEventPublisher eventPublisher;

	@Autowired
	public EmployeeServiceImpl(
		@Lazy EmployeeRepository employeeRepository,
		@Lazy DepartmentServiceInterface departmentServiceInterface,
		@Lazy FileServiceInterface fileServiceInterface,
		@Lazy ChangeLogServiceInterface changeLogServiceInterface,
		@Lazy EmployeeMapper employeeMapper,
		@Lazy ApplicationEventPublisher eventPublisher) {
		this.employeeRepository = employeeRepository;
		this.departmentServiceInterface = departmentServiceInterface;
		this.fileServiceInterface = fileServiceInterface;
		this.changeLogServiceInterface = changeLogServiceInterface;
		this.employeeMapper = employeeMapper;
		this.eventPublisher = eventPublisher;
	}

	private Instant updateTime = Instant.EPOCH;

	@Override
	@Transactional
	public EmployeeDto creatEmployee(EmployeeCreateRequest employeeCreateRequest, MultipartFile profileImg,
		String ipAddress) throws
		IOException {
		Department usingDepartment = departmentServiceInterface.findDepartmentEntityById(
			employeeCreateRequest.departmentId());
		if (usingDepartment == null) {
			throw new NoSuchElementException("Department 아이디가 존재하지 않음");
		}

		File file = null;
		if (profileImg != null) {
			file = fileServiceInterface.createImgFile(profileImg);
		}

		String uniquePart = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 14);
		String employeeNumber = "EMP-" + "년도" + uniquePart;
		Employee employee = Employee.createEmployee(employeeCreateRequest.name(), employeeCreateRequest.email(),
			employeeNumber, employeeCreateRequest.position(),
			employeeCreateRequest.hireDate(), EmployeeStatus.ACTIVE, file, usingDepartment);

		EmployeeDto newEmployee=employeeMapper.employeeToDto(employee);
		//만들어지면 넣기
		// updateTime = Instant.now();
		// String memo;
		// if (employeeCreateRequest.memo() == null) {
		// 	memo = "신규 직원 등록";
		// } else {
		// 	memo = employeeCreateRequest.memo();
		// }
		// log.info("이벤트 발행시작...");
		// //이벤트 발행 (before = null, after = 새 Employee)
		// eventPublisher.publishEvent(new EmployeeEvent(
		// 	ChangeLogType.CREATED, employee.getEmployeeNumber(), memo, "127.0.0.1", null,
		// 	employeeMapper.employeeToDto(employee)
		// ));
		// log.info("change-logs 생성 완료");

		return employeeMapper.employeeToDto(employeeRepository.save(employee));
	}

	@Override
	@Transactional(readOnly = true)
	public EmployeeDto findEmployeeById(Long Id) {
		Employee employee = employeeRepository.findById(Id)
			.orElseThrow(() -> new NoSuchElementException("Message with id " + Id + " not found"));
		return employeeMapper.employeeToDto(employee);
	}

	@Override
	@Transactional(readOnly = true)
	public CursorPageResponseEmployeeDto findEmployeeList(String nameOrEmail, String employeeNumber,
		String departmentName, String position, String hireDateFrom, String hireDateTo, String status, Long idAfter,
		String cursor, int size, String sortField, String sortDirection) {

		return null;
	}

	@Override
	@Transactional(readOnly = true)
	public List<EmployeeDto> getEmployeeAllList() {
		List<Employee> find = employeeRepository.findAll();
		return find.stream()
			.map(employeeMapper::employeeToDto)
			.toList();
	}

	@Override
	@Transactional
	public boolean deleteEmployee(Long id, String ipAddress) {
		if (employeeRepository.existsById(id)) {

			Employee employee = employeeRepository.findById(id).get();
			fileServiceInterface.deleteFile(employee.getFile());
			employeeRepository.deleteById(id);
			//로그작업
			updateTime = Instant.now();

			// log.info("이벤트 발행시작...");
			// eventPublisher.publishEvent(new EmployeeEvent(
			// 	ChangeLogType.DELETED, employee.getEmployeeNumber(), "직원 삭제", "127.0.0.1", beforeEmployee, null
			// ));
			// log.info("change-logs 생성 완료");

			return true;
		}
		return false;
	}

	@Override
	@Transactional
	public EmployeeDto updateEmployee(Long id, EmployeeUpdateRequest employeeUpdateRequest,
		MultipartFile profileImg, String ipAddress) throws
		IOException {

		Employee employee = employeeRepository.findById(id)
			.orElseThrow(() -> new NoSuchElementException("Message with id " + id + " not found"));
		EmployeeDto newEmployee=employeeMapper.employeeToDto(employee);
		File file = null;

		// 변경 전 상태 저장 (깊은 복사)
		EmployeeDto beforeEmployee = employeeMapper.employeeToDto(employee);

		Department usingDepartment = departmentServiceInterface.findDepartmentEntityById(
			employeeUpdateRequest.departmentId());
		if (usingDepartment == null) {
			throw new NoSuchElementException("Department 아이디가 존재하지 않음");
		}
		EmployeeStatus status = EmployeeStatus.valueOf(employeeUpdateRequest.status().toUpperCase());

		employee.updateName(employeeUpdateRequest.name());
		employee.updateEmail(employeeUpdateRequest.email());
		employee.updateDepartment(usingDepartment);
		employee.updatePosition(employeeUpdateRequest.position());
		employee.updateHireDateFrom(employeeUpdateRequest.hireDate());
		employee.updateStatus(status);

		if (profileImg != null) {
			fileServiceInterface.deleteFile(employee.getFile());
			file = fileServiceInterface.createImgFile(profileImg);
			employee.updateFile(file);
		}
		EmployeeDto oldEmployee=employeeMapper.employeeToDto(employee);
		//만들어지면 넣기
		//changeLogServiceInterface.createChangeLog();

		updateTime = Instant.now();

		//만들어지면 넣기(dto변환)
		EmployeeDto afterEmployee = employeeMapper.employeeToDto(employee);
		String memo;
		if (employeeUpdateRequest.memo() == null) {
			memo = "직원 정보 수정";
		} else {
			memo = employeeUpdateRequest.memo();
		}
		// 이벤트 발행 (before = 기존 Employee, after = 수정된 Employee)
		// log.info("이벤트 발행시작...");
		// eventPublisher.publishEvent(new EmployeeEvent(
		// 	ChangeLogType.UPDATED, employee.getEmployeeNumber(), memo, "127.0.0.1",
		// 	beforeEmployee, afterEmployee
		// ));
		// log.info("change-logs 생성 완료");

		return employeeMapper.employeeToDto(employee);
	}

	@Override
	@Transactional(readOnly = true)
	public List<EmployeeTrendDto> getEmployeeTrend(Instant startedAt, Instant endedAt, String gap) {

		List<Object[]> results = employeeRepository.findEmployeeTrend(startedAt, endedAt, gap);

		List<EmployeeTrendDto> trends = new ArrayList<>();
		long previousCount = 0;

		for (Object[] row : results) {
			Instant date = ((Timestamp)row[0]).toInstant();
			long count = ((Number)row[1]).longValue();

			long change = count - previousCount;
			double changeRate = (previousCount == 0) ? 0.0 : (change * 100.0 / previousCount);
			trends.add(new EmployeeTrendDto(date, count, change, changeRate));
			previousCount = count;
		}

		return trends;
	}

	@Override
	@Transactional(readOnly = true)
	public List<EmployeeDistributionDto> getEmployeeDistributaion(String groupBy, String status) {

		List<EmployeeDistributionDto> distribution;
		if (groupBy.equals("position")) {
			return convertDistributionPosition(status);
		} else if (groupBy.equals("department")) {
			return convertDistributionDepartment(status);
		} else {
			return convertDistributionDepartment(status);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Long countEmployee(String status, Instant startedAt, Instant endedAt) {
		EmployeeStatus findStatus = EmployeeStatus.valueOf(status.toUpperCase());
		return employeeRepository.countByStatusAndHireDateFromBetween(findStatus, startedAt, endedAt);
	}

	private List<EmployeeDistributionDto> convertDistributionPosition(String status) {
		List<EmployeeDistributionDto> distribution = new ArrayList<>();
		List<Object[]> results = employeeRepository.findDistributionPosition(
			EmployeeStatus.valueOf(status.toUpperCase()));
		for (Object[] row : results) {
			String positionName = (String)row[0];
			Long totalEmployees = (Long)row[1];
			Long activeEmployees = (Long)row[2];
			double ratio = (activeEmployees == 0) ? 0.0 : ((double)totalEmployees * 100 / activeEmployees);
			distribution.add(new EmployeeDistributionDto(positionName, totalEmployees,
				ratio));
		}
		return distribution;
	}

	private List<EmployeeDistributionDto> convertDistributionDepartment(String status) {
		List<EmployeeDistributionDto> distribution = new ArrayList<>();
		List<Object[]> results = employeeRepository.findDistributionDepartment(
			EmployeeStatus.valueOf(status.toUpperCase()));
		for (Object[] row : results) {
			Long departmentId = (Long)row[0];
			Long totalEmployees = (Long)row[1];
			Long activeEmployees = (Long)row[2];
			double ratio = (activeEmployees == 0) ? 0.0 : ((double)totalEmployees * 100 / activeEmployees);
			String departmentName = departmentServiceInterface.findDepartmentById(departmentId).name();
			distribution.add(new EmployeeDistributionDto(departmentName, totalEmployees,
				ratio));
		}
		return distribution;
	}

	private String escapeSpecialCharacters(String searchTerm) {
		if (searchTerm == null) {
			return null;
		}
		Pattern specialCharacters = Pattern.compile("[\\(\\)\\[\\]\\{\\}\\^\\$\\.\\*\\+\\?\\|\\\\]");
		Matcher matcher = specialCharacters.matcher(searchTerm);
		return matcher.replaceAll("\\\\$0");
	}

	@Override
	public Instant getUpdateTime() {
		return updateTime;
	}
}
