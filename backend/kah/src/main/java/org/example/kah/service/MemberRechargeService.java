package org.example.kah.service;

import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.publicapi.MemberRechargeItemView;
import org.example.kah.security.AuthenticatedUser;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface MemberRechargeService {

    CursorPageResponse<MemberRechargeItemView> listMine(AuthenticatedUser currentUser, int size, String cursor);

    MemberRechargeItemView create(AuthenticatedUser currentUser, java.math.BigDecimal amount, MultipartFile screenshot, String payerRemark);

    Resource loadMineScreenshot(AuthenticatedUser currentUser, Long rechargeId);
}