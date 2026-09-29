package vn.edu.medmaintenance.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class PageQuery {
    @NotNull
    @Min(0)
    private Integer page = 0;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer size = 20;

    private String sort;

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
}
