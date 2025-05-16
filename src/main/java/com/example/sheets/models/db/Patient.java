package com.example.sheets.models.db;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "patients")
public class Patient {
    @Id
    private String id;
    private String name;
    private String dateOfBirth;
    private String disease;
    private String bedType;
    private String hospitalId;
}
