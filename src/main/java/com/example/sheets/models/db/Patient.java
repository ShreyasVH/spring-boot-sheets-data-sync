package com.example.sheets.models.db;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.sql.Date;

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
    private Date dateOfBirth;
    private String disease;
    private String bedType;
    private String hospitalId;
}
