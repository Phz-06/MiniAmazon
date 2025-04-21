package edu.ece568.MiniAmazon.dto.response;

import edu.ece568.MiniAmazon.model.WareHouse;
import lombok.Data;

@Data
public class WarehouseDTO {
    private Long id;
    private String name;
    private int xCoordinate;
    private int yCoordinate;
    
    public static WarehouseDTO fromEntity(WareHouse wareHouse) {
        WarehouseDTO dto = new WarehouseDTO();
        dto.setId(wareHouse.getId());
        dto.setName(wareHouse.getName());
        dto.setXCoordinate(wareHouse.getXCoordinate());
        dto.setYCoordinate(wareHouse.getYCoordinate());
        return dto;
    }
} 