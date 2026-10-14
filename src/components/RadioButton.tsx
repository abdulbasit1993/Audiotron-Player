import React, { useMemo } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ViewStyle,
  TextStyle,
} from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { ThemeColors } from '../theme/colors';

export interface RadioOption {
  label: string;
  value: string | number;
  disabled?: boolean;
}

interface RadioGroupProps {
  options: RadioOption[];
  value: string | number | null;
  onChange: (value: string | number) => void;
  layout?: 'horizontal' | 'vertical';
  activeColor?: string;
  inactiveColor?: string;
  size?: number;
  containerStyle?: ViewStyle;
  itemStyle?: ViewStyle;
  labelStyle?: TextStyle;
}

export const RadioGroup: React.FC<RadioGroupProps> = ({
  options,
  value,
  onChange,
  layout = 'vertical',
  activeColor,
  inactiveColor,
  size = 20,
  containerStyle,
  itemStyle,
  labelStyle,
}) => {
  const { colors } = useTheme();
  const styles = useMemo(() => createStyles(colors), [colors]);

  const resolvedActiveColor = activeColor ?? colors.primary;
  const resolvedInactiveColor = inactiveColor ?? colors.radioInactive;

  return (
    <View
      style={[
        styles.container,
        layout === 'horizontal'
          ? styles.horizontalContainer
          : styles.verticalContainer,
        containerStyle,
      ]}
    >
      {options.map(option => {
        const isSelected = value === option.value;
        const isDisabled = option.disabled;

        const circleDynamicStyle = {
          width: size,
          height: size,
          borderRadius: size / 2,
          borderColor: isSelected
            ? resolvedActiveColor
            : resolvedInactiveColor,
        };

        const dotDynamicStyle = {
          width: size / 2,
          height: size / 2,
          borderRadius: size / 4,
          backgroundColor: isDisabled
            ? colors.radioDisabled
            : resolvedActiveColor,
        };

        const labelDynamicFontSize = {
          fontSize: size * 0.8,
        };

        return (
          <TouchableOpacity
            key={String(option.value)}
            style={[
              styles.radioButton,
              layout === 'horizontal'
                ? styles.horizontalItem
                : styles.verticalItem,
              isDisabled && styles.disabledItem,
              itemStyle,
            ]}
            activeOpacity={0.8}
            disabled={isDisabled}
            onPress={() => !isDisabled && onChange(option.value)}
            accessibilityRole="radio"
            accessibilityState={{ checked: isSelected, disabled: isDisabled }}
            accessibilityLabel={option.label}
          >
            {/* Outer Circle */}
            <View
              style={[
                styles.outerCircle,
                circleDynamicStyle,
                isDisabled && styles.disabledOuterCircle,
              ]}
            >
              {/* Inner Dot (Visible when selected) */}
              {isSelected && (
                <View style={[styles.innerCircle, dotDynamicStyle]} />
              )}
            </View>

            {/* Label */}
            <Text
              style={[
                styles.label,
                labelDynamicFontSize,
                isDisabled ? styles.disabledLabel : styles.enabledLabel,
                labelStyle,
              ]}
            >
              {option.label}
            </Text>
          </TouchableOpacity>
        );
      })}
    </View>
  );
};

const createStyles = (colors: ThemeColors) =>
  StyleSheet.create({
    container: {
      marginVertical: 4,
    },
    verticalContainer: {
      flexDirection: 'column',
    },
    horizontalContainer: {
      flexDirection: 'row',
      flexWrap: 'wrap',
    },
    radioButton: {
      flexDirection: 'row',
      alignItems: 'center',
      marginVertical: 8,
    },
    verticalItem: {
      marginRight: 0,
    },
    horizontalItem: {
      marginRight: 16,
    },
    outerCircle: {
      borderWidth: 2,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: 'transparent',
    },
    disabledOuterCircle: {
      borderColor: colors.radioDisabled,
    },
    innerCircle: {},
    label: {
      marginLeft: 10,
    },
    enabledLabel: {
      color: colors.text,
    },
    disabledItem: {
      opacity: 0.6,
    },
    disabledLabel: {
      color: colors.textMuted,
    },
  });

