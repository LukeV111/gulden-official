<template>
  <div class="holding-account">
    <portal to="header-slot">
      <section class="header flex-row">
        <main-header
          class="info"
          :title="account.label"
          :subtitle="account.balance.toFixed(2)"
        />
        <div class="settings flex-col">
          <span class="button" @click="setRightSidebar('Settings')">
            <fa-icon :icon="['fal', 'cog']" />
          </span>
        </div>
      </section>
    </portal>

    <gulden-section class="align-right">
      {{ $t("holding_account.compound_earnings") }}
      <toggle-button
        :value="isCompounding"
        :color="{ checked: '#009572', unchecked: '#ddd' }"
        :labels="{
          checked: $t('common.on'),
          unchecked: $t('common.off')
        }"
        :sync="true"
        :speed="0"
        :height="16"
        :width="44"
        @change="toggleCompounding"
      />
    </gulden-section>

    <gulden-section class="holding-information">
      <h4>{{ $t("common.information") }}</h4>

      <div class="flex-row">
        <div>{{ $t("holding_account.status") }}</div>
        <div>{{ accountStatus }}</div>
      </div>
      <div class="flex-row">
        <div>{{ "Parts" }}</div>
        <div>{{ accountParts }}</div>
      </div>

      <div class="flex-row">
        <div>{{ $t("holding_account.gulden_locked") }}</div>
        <div>{{ accountAmountLocked }}</div>
      </div>
      <div class="flex-row">
        <div>{{ $t("holding_account.gulden_earned") }}</div>
        <div>{{ accountAmountEarned }}</div>
      </div>
      <div class="flex-row">
        <div>{{ $t("holding_account.locked_from_block") }}</div>
        <div>{{ lockedFrom }}</div>
      </div>
      <div class="flex-row">
        <div>{{ $t("holding_account.locked_until_block") }}</div>
        <div>{{ lockedUntil }}</div>
      </div>
      <div class="flex-row">
        <div>{{ $t("holding_account.lock_duration") }}</div>
        <div>{{ lockDuration }} {{ $t("holding_account.blocks") }}</div>
      </div>
      <div class="flex-row">
        <div>{{ $t("holding_account.remaining_lock_period") }}</div>
        <div>{{ remainingLockPeriod }} {{ $t("holding_account.blocks") }}</div>
      </div>

      <div class="flex-row">
        <div>{{ $t("holding_account.required_earnings_frequency") }}</div>
        <div>
          {{ requiredEarningsFrequency }} {{ $t("holding_account.blocks") }}
        </div>
      </div>
      <div class="flex-row">
        <div>{{ $t("holding_account.expected_earnings_frequency") }}</div>
        <div>
          {{ expectedEarningsFrequency }} {{ $t("holding_account.blocks") }}
        </div>
      </div>

      <div class="flex-row">
        <div>{{ $t("holding_account.account_weight") }}</div>
        <div>{{ accountWeight }}</div>
      </div>
      <div class="flex-row">
        <div>{{ $t("holding_account.network_weight") }}</div>
        <div>{{ networkWeight }}</div>
      </div>
    </gulden-section>

    <portal to="footer-slot">
      <section class="footer">
        <span class="button" @click="renewAccount" v-if="renewButtonVisible">
          <fa-icon :icon="['fal', 'redo-alt']" />
          {{ $t("buttons.renew") }}
        </span>
        <span class="button" @click="emptyAccount" v-if="sendButtonVisible">
          <fa-icon :icon="['fal', 'arrow-from-bottom']" />
          {{ $t("buttons.send") }}
        </span>
      </section>
    </portal>

    <portal to="sidebar-right">
      <component
        v-if="rightSidebar"
        :is="rightSidebar"
        v-bind="rightSidebarProps"
      />
    </portal>
  </div>
</template>

<script>
import { WitnessController } from "../../../unity/Controllers";
import EventBus from "../../../EventBus";
import SendGulden from "../MiningAccount/SendGulden";
import RenewAccount from "./RenewAccount";
import AccountSettings from "../AccountSettings";

let timeout;

export default {
  name: "HoldingAccount",
  props: {
    account: null
  },
  data() {
    return {
      rightSection: null,
      rightSectionComponent: null,
      statistics: null,
      isCompounding: false,
      rightSidebar: null
    };
  },
  computed: {
    accountStatus() {
      return this.getStatistics("account_status");
    },
    accountAmountLocked() {
      return this.getStatistics("account_amount_locked") / 100000000;
    },
    accountAmountEarned() {
      return this.account.balance - this.accountAmountLocked || 0;
    },
    lockedFrom() {
      return this.getStatistics("account_initial_lock_creation_block_height");
    },
    lockedUntil() {
      return (
        this.getStatistics("account_initial_lock_creation_block_height") +
        this.getStatistics("account_initial_lock_period_in_blocks")
      );
    },
    lockDuration() {
      return this.getStatistics("account_initial_lock_period_in_blocks");
    },
    remainingLockPeriod() {
      return this.getStatistics("account_remaining_lock_period_in_blocks");
    },
    requiredEarningsFrequency() {
      return this.getStatistics("account_expected_witness_period_in_blocks");
    },
    expectedEarningsFrequency() {
      return this.getStatistics("account_estimated_witness_period_in_blocks");
    },
    accountWeight() {
      return this.getStatistics("account_weight");
    },
    networkWeight() {
      return this.getStatistics("network_tip_total_weight");
    },
    accountParts() {
      return this.getStatistics("account_parts");
    },
    rightSidebarProps() {
      if (this.rightSidebar === AccountSettings) {
        return { account: this.account };
      }
      return null;
    },
    sendButtonDisabled() {
      return this.account.spendable > 0;
    },
    sendButtonVisible() {
      return this.sendButtonDisabled && this.rightSidebar !== SendGulden;
    },
    renewButtonVisible() {
      return (
        this.accountStatus === "expired" && this.rightSidebar !== RenewAccount
      );
    }
  },
  mounted() {
    EventBus.$on("close-right-sidebar", this.closeRightSidebar);
  },
  beforeDestroy() {
    EventBus.$off("close-right-sidebar", this.closeRightSidebar);
    clearTimeout(timeout);
  },
  created() {
    this.initialize();
  },
  watch: {
    account() {
      this.initialize();
    },
    sendButtonDisabled() {
      if (this.rightSidebar !== null && this.sendButtonDisabled === false)
        this.closeRightSidebar();
    }
  },
  methods: {
    initialize() {
      this.updateStatistics();
      this.isCompounding = WitnessController.IsAccountCompounding(
        this.account.UUID
      );
    },
    getStatistics(which) {
      return this.statistics[which] || null;
    },
    updateStatistics() {
      clearTimeout(timeout);
      this.statistics = WitnessController.GetAccountWitnessStatistics(
        this.account.UUID
      );
      timeout = setTimeout(this.updateStatistics, 5000);
    },
    toggleCompounding() {
      WitnessController.SetAccountCompounding(
        this.account.UUID,
        !this.isCompounding
      );
      this.isCompounding = !this.isCompounding;
    },
    setRightSidebar(name) {
      switch (name) {
        case "Settings":
          this.rightSidebar = AccountSettings;
          break;
      }
    },
    closeRightSidebar() {
      this.rightSidebar = null;
      this.txHash = null;
    },
    emptyAccount() {
      this.rightSidebar = SendGulden;
    },
    renewAccount() {
      this.rightSidebar = RenewAccount;
    }
  }
};
</script>

<style lang="less" scoped>
.header {
  & > .info {
    width: calc(100% - 26px);
    padding-right: 10px;
  }

  & > .settings {
    font-size: 16px;
    padding: calc((var(--header-height) - 40px) / 2) 0;

    & span {
      padding: 10px;
      cursor: pointer;

      &:hover {
        background: #f5f5f5;
      }
    }
  }
}

.holding-information {
  & .flex-row > div {
    line-height: 18px;
  }
  & .flex-row :first-child {
    min-width: 220px;
  }
}

// todo: .footer styles below are copy/pasted from SpendingAccount/index.vue, maybe move to parent
.footer {
  text-align: center;
  line-height: calc(var(--footer-height) - 1px);

  & svg {
    font-size: 14px;
    margin-right: 5px;
  }

  & .button {
    display: inline-block;
    padding: 0 20px 0 20px;
    line-height: 32px;
    font-weight: 500;
    font-size: 1em;
    color: var(--primary-color);
    text-align: center;
    cursor: pointer;

    &:hover {
      background-color: #f5f5f5;
    }
  }
}
</style>
