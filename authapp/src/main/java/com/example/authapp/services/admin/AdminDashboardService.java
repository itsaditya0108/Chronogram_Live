package com.example.authapp.services.admin;

import com.example.authapp.dto.admin.AdminDashboardResponse;
import com.example.authapp.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardService {

    private final UserRepository userRepository;

    public AdminDashboardService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AdminDashboardResponse getDashboardStats() {

        long total = userRepository.count();
        long active = userRepository.countByStatus_Id("01");
        long inactive = userRepository.countByStatus_Id("02");
        long blocked = userRepository.countByStatus_Id("03");
        long deleted = userRepository.countByStatus_Id("04");

        return new AdminDashboardResponse(total, active, inactive, blocked, deleted);
    }
}
