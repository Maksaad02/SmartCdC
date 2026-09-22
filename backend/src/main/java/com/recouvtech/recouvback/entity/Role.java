package com.recouvtech.recouvback.entity;


import com.recouvtech.recouvback.entity.enums.RoleAgent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Entity
public class Role {
    //Getters and Setters
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Enumerated(EnumType.STRING)
    private RoleAgent nom;


}