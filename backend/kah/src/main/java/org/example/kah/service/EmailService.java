package org.example.kah.service;


import org.example.kah.common.ApiResponse;
import org.example.kah.dto.publicapi.CodeSend;
import org.example.kah.dto.publicapi.MemberAuthResponse;
import org.example.kah.dto.publicapi.MemberLoginMailRqs;
import org.example.kah.dto.publicapi.MemberRegisMailRsp;

public interface EmailService {
     MemberAuthResponse login(MemberLoginMailRqs rqs);

    ApiResponse sendEmail(CodeSend rqs);

    MemberAuthResponse register(MemberRegisMailRsp rqs);
}
