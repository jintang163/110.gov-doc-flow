package com.gov.gw.doc;

import com.gov.gw.template.DocTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** 发文字号服务：前缀〔年份〕序号号，序号按模板+年度递增，作废号不回收 */
@Service
public class NumberService {
    private final DocNoCounterRepo counterRepo;

    public NumberService(DocNoCounterRepo counterRepo) {
        this.counterRepo = counterRepo;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public synchronized String nextNumber(DocTemplate template) {
        int year = LocalDate.now().getYear();
        DocNoCounter counter = counterRepo.findByTemplateIdAndYearNo(template.getId(), year)
                .orElseGet(() -> {
                    DocNoCounter c = new DocNoCounter();
                    c.setTemplateId(template.getId());
                    c.setYearNo(year);
                    c.setNextNo(1);
                    return c;
                });
        int no = counter.getNextNo();
        counter.setNextNo(no + 1);
        counterRepo.save(counter);
        return template.getNoPrefix() + "〔" + year + "〕" + no + "号";
    }
}
