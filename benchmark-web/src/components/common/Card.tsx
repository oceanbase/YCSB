import React from 'react';
import './Card.css';

interface CardProps {
  title?: string;
  subtitle?: string;
  icon?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
  actions?: React.ReactNode;
  collapsed?: boolean;
  onToggle?: () => void;
}

export const Card: React.FC<CardProps> = ({
  title,
  subtitle,
  icon,
  children,
  className = '',
  actions,
  collapsed,
  onToggle,
}) => {
  const isCollapsible = collapsed !== undefined;

  return (
    <div className={`card ${collapsed ? 'card-collapsed' : ''} ${className}`}>
      {(title || actions) && (
        <div 
          className={`card-header ${isCollapsible ? 'card-header-clickable' : ''}`}
          onClick={isCollapsible ? onToggle : undefined}
        >
          <div className="card-header-left">
            {icon && <span className="card-icon">{icon}</span>}
            <div className="card-titles">
              {title && <h3 className="card-title">{title}</h3>}
              {subtitle && <p className="card-subtitle">{subtitle}</p>}
            </div>
          </div>
          <div className="card-header-right">
            {actions}
            {isCollapsible && (
              <span className={`card-collapse-icon ${collapsed ? 'collapsed' : ''}`}>
                ▼
              </span>
            )}
          </div>
        </div>
      )}
      {!collapsed && <div className="card-body">{children}</div>}
    </div>
  );
};

