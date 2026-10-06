package com.amusement.park.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amusement.park.common.BizException;
import com.amusement.park.common.ParkShowTime;
import com.amusement.park.dto.ShowInput;
import com.amusement.park.dto.ShowScheduleItem;
import com.amusement.park.entity.Show;
import com.amusement.park.repository.ShowRepository;

/**
 * 园区演出业务。
 *
 * <p>核心规则：预订座位不得超过演出容量（capacity），超出则抛出中文
 * {@link BizException} 拒绝，避免超售。</p>
 *
 * <p>时刻口径：所有演出时刻一律以园区固定时区（Asia/Shanghai）解释并排序。
 * 历史值带不带偏移量都经 {@link ParkShowTime} 归一成上海挂钟值后入库；
 * 列表按解释后的真实时刻从早到晚，未排期（无时刻）统一沉到末尾，
 * 时刻相同时按演出编号 {@code showCode} 确定先后，保证次序确定且可重复，
 * 不依赖数据库返回顺序、容器或客户端时区。</p>
 */
@Service
public class ShowService {

    /**
     * 列表排序：园区时刻升序（null 末尾）→ 演出编号升序（null 末尾，正常数据编号非空）。
     */
    static final Comparator<Show> SCHEDULE_ORDER = Comparator
            .comparing(Show::getShowAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Show::getShowCode, Comparator.nullsLast(Comparator.naturalOrder()));

    private final ShowRepository showRepository;
    private final Clock parkClock;

    public ShowService(ShowRepository showRepository, Clock parkClock) {
        this.showRepository = showRepository;
        this.parkClock = parkClock;
    }

    /**
     * 演出排期列表：已按园区真实时刻从早到晚排序，未排期沉到末尾，
     * 同一时刻按演出编号排序。返回项显式携带园区日期/时间字符串。
     */
    public List<ShowScheduleItem> listSchedule() {
        return showRepository.findAll().stream()
                .sorted(SCHEDULE_ORDER)
                .map(ShowScheduleItem::of)
                .toList();
    }

    public Show get(Long id) {
        return showRepository.findById(id)
                .orElseThrow(() -> new BizException("演出不存在：" + id));
    }

    @Transactional
    public Show create(ShowInput input) {
        if (input.getShowCode() == null || input.getShowCode().isBlank()) {
            throw new BizException("演出编号不能为空");
        }
        if (input.getShowName() == null || input.getShowName().isBlank()) {
            throw new BizException("演出名称不能为空");
        }
        if (input.getCapacity() == null || input.getCapacity() <= 0) {
            throw new BizException("演出容量必须大于 0");
        }
        Show show = new Show();
        show.setShowCode(input.getShowCode().trim());
        show.setShowName(input.getShowName().trim());
        show.setVenue(input.getVenue());
        show.setCapacity(input.getCapacity());
        show.setBooked(input.getBooked() == null ? 0 : Math.max(0, input.getBooked()));
        if (show.getBooked() > show.getCapacity()) {
            throw new BizException("初始预订 " + show.getBooked() + " 已超过容量 " + show.getCapacity());
        }
        applyShowAt(show, input.getShowAtRaw());
        show.setCreatedAt(LocalDateTime.now(parkClock));
        return showRepository.save(show);
    }

    @Transactional
    public Show update(Long id, ShowInput input) {
        Show show = get(id);
        if (input.getShowName() != null && !input.getShowName().isBlank()) {
            show.setShowName(input.getShowName().trim());
        }
        if (input.getVenue() != null) {
            show.setVenue(input.getVenue());
        }
        if (input.getCapacity() != null) {
            if (input.getCapacity() <= 0) {
                throw new BizException("演出容量必须大于 0");
            }
            if (show.getBooked() > input.getCapacity()) {
                throw new BizException("容量不能小于已预订座位数（已订 " + show.getBooked() + "）");
            }
            show.setCapacity(input.getCapacity());
        }
        if (input.getShowAtRaw() != null) {
            // 传空串表示清空排期；传具体字面量则重新按园区时区解释
            applyShowAt(show, input.getShowAtRaw());
        }
        return showRepository.save(show);
    }

    /**
     * 解释原始时刻字面量（带/不带偏移量）为上海园区挂钟值并连同原文一起写入，
     * 保证历史值解释一致且可追溯。
     */
    private void applyShowAt(Show show, String raw) {
        String normalized = raw == null ? null : raw.trim();
        show.setShowAtRaw(normalized == null || normalized.isEmpty() ? null : normalized);
        show.setShowAt(ParkShowTime.parseRejectable(raw));
    }

    /**
     * 预订座位。超出容量则拒绝。
     *
     * @param seats 本次预订座位数
     */
    @Transactional
    public Show reserve(Long id, Integer seats) {
        Show show = get(id);
        if (seats == null || seats <= 0) {
            throw new BizException("预订座位数必须大于 0");
        }
        int after = show.getBooked() + seats;
        if (after > show.getCapacity()) {
            throw new BizException("超出演出容量，剩余座位仅 "
                    + (show.getCapacity() - show.getBooked()) + " 个，无法再预订 " + seats + " 个");
        }
        show.setBooked(after);
        return showRepository.save(show);
    }
}
