package com.sevenb.user_manager.dto;

import lombok.Data;

@Data
public class PersonResponseDto {

    private Long id;
    private String firstName;
    private String lastName;
    private String documentType;
    private String documentNumber;
    private String email;
    private String phone;
    private String address;
    private String taxCondition;
}
