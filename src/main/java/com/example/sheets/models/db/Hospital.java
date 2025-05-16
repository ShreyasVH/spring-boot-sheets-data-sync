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
@Table(name = "hospitals")
public class Hospital {
    @Id
    private String id;
    private String name;
    private String location;
    private int generalBedCount;
    private int icuBedCount;
}
