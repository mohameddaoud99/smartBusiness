import Aura from '@primeng/themes/aura';
import { definePreset } from '@primeng/themes';

/**
 * SmartBusiness visual identity.
 * Aura provides the component structure — we override colors, radius and density
 * so the app has its own look instead of the default PrimeNG appearance.
 */
export const SmartBusinessPreset = definePreset(Aura, {

  primitive: {
    // Sharper corners read as more "business tool", less "consumer app"
    borderRadius: {
      none: '0',
      xs: '3px',
      sm: '4px',
      md: '6px',
      lg: '8px',
      xl: '12px'
    }
  },

  semantic: {
    primary: {
      50: '#eef2ff',
      100: '#e0e7ff',
      200: '#c7d2fe',
      300: '#a5b4fc',
      400: '#818cf8',
      500: '#6366f1',
      600: '#4f46e5',
      700: '#4338ca',
      800: '#3730a3',
      900: '#312e81',
      950: '#1e1b4b'
    },

    // Compact form fields — ERP screens fit more information
    formField: {
      paddingX: '0.625rem',
      paddingY: '0.5rem',
      sm: { fontSize: '0.8125rem', paddingX: '0.5rem', paddingY: '0.375rem' },
      borderRadius: '{border.radius.md}'
    },

    content: {
      borderRadius: '{border.radius.lg}'
    },

    colorScheme: {
      light: {
        primary: {
          color: '{primary.600}',
          contrastColor: '#ffffff',
          hoverColor: '{primary.700}',
          activeColor: '{primary.800}'
        },
        // Neutral slate surfaces keep status colors the only saturated thing on screen
        surface: {
          0: '#ffffff',
          50: '#f8fafc',
          100: '#f1f5f9',
          200: '#e2e8f0',
          300: '#cbd5e1',
          400: '#94a3b8',
          500: '#64748b',
          600: '#475569',
          700: '#334155',
          800: '#1e293b',
          900: '#0f172a',
          950: '#020617'
        }
      }
    }
  }
});
