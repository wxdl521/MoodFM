package com.moodfm.service.platform;

import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.domain.vo.PlatformBindingVO;

import java.util.List;
import java.util.Map;

public interface PlatformBindingService {

    Map<String, Object> generateQrCode(Long userId, String platform);

    Map<String, Object> checkQrStatus(Long userId, String platform, String key);

    void bindByCookie(Long userId, String platform, String cookie);

    String sendPhoneCode(Long userId, String platform, String phone);

    PlatformBindingVO bindByPhone(Long userId, String platform, String phone, String code, String ticket);

    void unbind(Long userId, String platform);

    List<PlatformBindingVO> listBindings(Long userId);

    PlatformBinding getValidBinding(Long userId, String platform);

    PlatformBinding getDefaultBinding(Long userId);

    List<PlatformBinding> listAllValidBindings(Long userId);

    void setDefault(Long userId, String platform);
}
