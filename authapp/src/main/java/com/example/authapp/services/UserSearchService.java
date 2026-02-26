package com.example.authapp.services;

import com.example.authapp.dto.UserSearchResponse;
import com.example.authapp.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserSearchService {

    private final UserRepository userRepository;

    public UserSearchService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserSearchResponse> search(String query, int limit, Long selfUserId) {

        if (query == null)
            return List.of();

        String q = query.trim().toLowerCase();

        if (q.length() < 2)
            return List.of();

        Pageable pageable = PageRequest.of(0, Math.min(limit, 20));

        return userRepository.searchUsers(q,null, pageable)
                .stream()
                .filter(u -> !u.getId().equals(selfUserId))
                .map(u -> new UserSearchResponse(
                        u.getId(),
                        u.getName(),
                        u.getEmail(),
                        u.getPhone()))
                .toList();
    }

    public List<UserSearchResponse> getUsersByIds(java.util.Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty())
            return List.of();

        return userRepository.findAllById(userIds)
                .stream()
                .map(u -> new UserSearchResponse(
                        u.getId(),
                        u.getName(),
                        u.getEmail(),
                        u.getPhone()))
                .toList();
    }

}
