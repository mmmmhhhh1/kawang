package org.example.kah.service;

import org.example.kah.dto.admin.AdminRuntimeDetailsView;
import org.example.kah.dto.admin.AdminRuntimeOverviewView;
import java.util.List;

public interface AdminRuntimeService {

    AdminRuntimeOverviewView getOverview();

    AdminRuntimeDetailsView getDetails(List<String> sectionKeys);
}
