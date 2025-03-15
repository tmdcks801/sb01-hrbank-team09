package com.team09.sb01hrbank09.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.team09.sb01hrbank09.dto.entityDto.EmployeeDto;
import com.team09.sb01hrbank09.entity.Employee;

@Mapper(componentModel = "spring")
public interface EmployeeMpper {
	@Mapping(source = "department.id", target = "departmentId")
	@Mapping(source = "file.id", target = "profileImageId")
	EmployeeDto employeeToDto(Employee employee);

	//Employee dtoToEmployee(EmployeeDto employeeDto);
}
