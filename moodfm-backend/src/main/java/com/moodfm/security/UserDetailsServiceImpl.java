package com.moodfm.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodfm.domain.entity.User;
import com.moodfm.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String account) throws UsernameNotFoundException {
        User user;
        // JwtAuthFilter 传入的是 userId（纯数字字符串），登录时传入的是 email/phone
        if (account.matches("\\d+")) {
            user = userMapper.selectById(Long.parseLong(account));
        } else {
            user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, account)
                    .or()
                    .eq(User::getPhone, account));
        }

        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + account);
        }

        String role = user.getRole() != null ? user.getRole() : "USER";
        return new org.springframework.security.core.userdetails.User(
                String.valueOf(user.getId()),
                user.getPasswordHash(),
                user.getStatus() == 1,
                true,
                true,
                user.getLockUntil() == null || user.getLockUntil().isBefore(java.time.LocalDateTime.now()),
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }
}
