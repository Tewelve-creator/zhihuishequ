package com.community.auth.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RoleMenusRequest {
    private List<Long> menuIds = new ArrayList<>();
}
